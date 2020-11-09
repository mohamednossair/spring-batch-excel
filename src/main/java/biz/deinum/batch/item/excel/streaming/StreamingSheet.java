package biz.deinum.batch.item.excel.streaming;

import biz.deinum.batch.item.excel.Sheet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.Styles;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.StaxUtils;
import org.xml.sax.Attributes;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class StreamingSheet implements Sheet, Iterable<String[]>, Closeable {

	private final Log logger = LogFactory.getLog(StreamingSheet.class);

    private final String name;
    private final InputStream is;
	private final XMLStreamReader reader;
	private final ValueRetrievingContentsHandler contentHandler;
	private final XSSFSheetXMLHandler sheetHandler;

	private int rowCount;
	private int colCount;

	StreamingSheet(String name, InputStream is, SharedStrings sharedStrings, Styles styles) {
        this.name = name;
        this.is = is;
        this.contentHandler = new ValueRetrievingContentsHandler();
        this.sheetHandler = new XSSFSheetXMLHandler(styles, sharedStrings, this.contentHandler, false);

        try {
            this.reader = StaxUtils.createDefensiveInputFactory().createXMLStreamReader(is);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getNumberOfRows() {
        return this.rowCount;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String[] getRow(int rowNumber) {
        throw new UnsupportedOperationException("Getting row by index not supported when streaming.");
    }

    private String[] nextRow() {
		try {
			while (reader.hasNext()) {
				int type = reader.next();
				if (type == XMLStreamConstants.START_DOCUMENT) {
					sheetHandler.startDocument();
				} else if (type == XMLStreamConstants.END_DOCUMENT) {
					sheetHandler.endDocument();
					return null;
				} else if (type == XMLStreamConstants.CHARACTERS) {
					sheetHandler.characters(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
				} else if (type == XMLStreamConstants.START_ELEMENT) {
					String localName = reader.getLocalName();
					if ("dimension".equals(localName)) {
						String v = reader.getAttributeValue(null, "ref");
						if (v != null && v.indexOf(':') > -1) {
							CellRangeAddress range = CellRangeAddress.valueOf(v);
							int rowEnd = range.getLastRow();
							int rowStart = range.getFirstRow();
							this.rowCount = rowEnd - rowStart + 1;

							int colStart = range.getFirstColumn();
							int colEnd = range.getLastColumn();
							this.colCount = colEnd - colStart + 1;
						}
					} else {
						Attributes delegating = new AttributesAdapter(this.reader);
						sheetHandler.startElement(null, localName, null, delegating);
					}
				} else if (type == XMLStreamConstants.END_ELEMENT) {
					this.sheetHandler.endElement(null, reader.getLocalName(), null);
					String tag = reader.getLocalName();
					if ("row".equals(tag)) {
						if (logger.isTraceEnabled()) {
							logger.trace("Row ended, returning: " + StringUtils.arrayToCommaDelimitedString(this.contentHandler.getValues()));
						}
						return this.contentHandler.getValues();
					}
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Error reading file.", e);
		}
        return null;
    }

    @Override
    public void close() throws IOException {
        try {
            this.reader.close();
        } catch (XMLStreamException e) {
        }

        this.is.close();
    }

    @Override
    public Iterator<String[]> iterator() {
        return new Iterator<String[]>() {

        	private String[] currentRow;

            @Override
            public boolean hasNext() {
            	currentRow = nextRow();
                return currentRow != null;
            }

            @Override
            public String[] next() {
                return this.currentRow;
            }
        };
    }

    private class ValueRetrievingContentsHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

    	private final Log logger = LogFactory.getLog(ValueRetrievingContentsHandler.class);
    	private String[] values;

		@Override
		public void startRow(int rowNum) {
			if (logger.isTraceEnabled()) {
				logger.trace("Start processing row: " + rowNum);
			}
			// Prepare for this row
			if (values == null) {
				this.values = new String[colCount];
			}
			Arrays.fill(values, "");
		}

		@Override
		public void endRow(int rowNum) {
			if (logger.isTraceEnabled()) {
				logger.trace("End processing row: " + rowNum);
			}
		}

		@Override
		public void cell(String cellReference, String formattedValue, XSSFComment comment) {
			int col = new CellReference(cellReference).getCol();
			if (logger.isTraceEnabled()) {
				logger.trace("Setting value (" + cellReference + ") = " + formattedValue);
			}
			// This can happen if the dimensions cannot be read properly but there are still rows.
			// Create a copy of the existing array and append to it.
			if (values.length <= col) {
				String[] newValues = Arrays.copyOf(values, col + 1);
				Arrays.setAll(newValues, idx -> newValues[idx] == null ? "" : newValues[idx]);
				this.values = newValues;
			}
			values[col] = formattedValue;
		}

		public String[] getValues() {
			return Arrays.copyOf(this.values, this.values.length);
		}
	}

	/**
	 * Minimal adapter for {@code Attributes} so that it works with the
	 * {@code XSSFSheetXMLHandler}. Adapts an {@code XMLStreamReader} so that it
	 * can be used as an {@code org.xml.sax.Attributes} implementation.
	 */
	private static class AttributesAdapter implements Attributes {

		private final Map<String, String> attributes = new HashMap<>();

		private AttributesAdapter(XMLStreamReader delegate) {
			for (int i = 0 ; i < delegate.getAttributeCount(); i++) {
				String name = delegate.getAttributeLocalName(i);
				String value = delegate.getAttributeValue(i);
				attributes.put(name, value);
			}
		}

		@Override
		public int getLength() {
			return attributes.size();
		}

		@Override
		public String getURI(int index) {
			return null;
		}

		@Override
		public String getLocalName(int index) {
			return null;
		}

		@Override
		public String getQName(int index) {
			return null;
		}

		@Override
		public String getType(int index) {
			return null;
		}

		@Override
		public String getValue(int index) {
			return null;
		}

		@Override
		public int getIndex(String uri, String localName) {
			return 0;
		}

		@Override
		public int getIndex(String qName) {
			return 0;
		}

		@Override
		public String getType(String uri, String localName) {
			return null;
		}

		@Override
		public String getType(String qName) {
			return null;
		}

		@Override
		public String getValue(String uri, String localName) {
			return attributes.get(localName);
		}

		@Override
		public String getValue(String qName) {
			return attributes.get(qName);
		}
	}
}
