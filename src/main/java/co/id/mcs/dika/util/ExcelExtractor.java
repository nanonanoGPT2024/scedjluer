package co.id.mcs.dika.util;

import java.io.InputStream;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import co.id.mcs.ptdika.MadMachine.Converter.MapperObject;
import co.id.mcs.ptdika.MadMachine.Converter.Model.MapperModel;
import co.id.mcs.ptdika.MadMachine.Mapper.MapperEngine;
import co.id.mcs.ptdika.MadMachine.Mapper.Model.MapperRule;

public class ExcelExtractor {

    @FunctionalInterface
    public interface RowHandler {
        void handle(Map<String, Object> row) throws Exception;
    }

    // ===================== PUBLIC API =====================

    public static void extract(
            java.io.File file,
            String originalFileName,
            int[] headerRows,
            int startValueRow,
            String startColumn,
            List<MapperRule> mapperRules,
            RowHandler rowHandler) throws Exception {

        if (originalFileName.toLowerCase().endsWith(".xlsx")) {
            streamingExtractXlsx(file, headerRows, startValueRow, startColumn, mapperRules, rowHandler);
            return;
        }

        try (Workbook workbook = WorkbookFactory.create(file)) {
            Result result = processWorkbook(workbook, headerRows, startValueRow, startColumn, mapperRules);
            for (Map<String, Object> row : result.get()) {
                rowHandler.handle(row);
            }
        }
    }

    public static void extract(
            java.io.File file,
            int[] headerRows,
            int startValueRow,
            String startColumn,
            List<MapperRule> mapperRules,
            RowHandler rowHandler) throws Exception {
        extract(file, file.getName(), headerRows, startValueRow, startColumn, mapperRules, rowHandler);
    }

    public static Result extract(
            InputStream inputStream,
            int[] headerRows,
            int startValueRow,
            String startColumn,
            List<MapperRule> mapperRules) throws Exception {

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            return processWorkbook(workbook, headerRows, startValueRow, startColumn, mapperRules);
        }
    }

    public static Result extract(
            java.io.File file,
            int[] headerRows,
            int startValueRow,
            String startColumn,
            List<MapperRule> mapperRules) throws Exception {

        List<Map<String, Object>> data = new ArrayList<>();
        extract(file, headerRows, startValueRow, startColumn, mapperRules, data::add);
        return new Result(new ArrayList<>(), data, mapperRules);
    }

    private static void streamingExtractXlsx(
            java.io.File file,
            int[] headerRows,
            int startValueRow,
            String startColumn,
            List<MapperRule> mapperRules,
            RowHandler rowHandler) throws Exception {

        try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ)) {
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader xssfReader = new XSSFReader(pkg);
            StylesTable styles = xssfReader.getStylesTable();
            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();

            if (iter.hasNext()) {
                try (InputStream sheetStream = iter.next()) {
                    StreamingSheetHandler handler = new StreamingSheetHandler(headerRows, startValueRow, startColumn,
                            mapperRules, rowHandler);

                    SAXParserFactory saxFactory = SAXParserFactory.newInstance();
                    saxFactory.setNamespaceAware(true);
                    SAXParser saxParser = saxFactory.newSAXParser();
                    XMLReader sheetParser = saxParser.getXMLReader();

                    ContentHandler contentHandler = new XSSFSheetXMLHandler(styles, strings, handler, false);
                    sheetParser.setContentHandler(contentHandler);
                    sheetParser.parse(new InputSource(sheetStream));
                }
            }
        }
    }

    private static class StreamingSheetHandler implements SheetContentsHandler {
        private final int[] headerRows;
        private final int startValueRow;
        private final int startColIndex;
        private final List<MapperRule> mapperRules;
        private final RowHandler rowHandler;

        List<String> headers = new ArrayList<>();

        private Map<Integer, String> currentRow = new HashMap<>();
        private int currentRowNum = -1;
        private Map<Integer, List<String>> headerBuffers = new HashMap<>();
        private int maxHeaderCol = -1;

        private final MapperEngine mapperEngine;

        public StreamingSheetHandler(int[] headerRows, int startValueRow, String startColumn,
                List<MapperRule> mapperRules, RowHandler rowHandler) {
            this.headerRows = headerRows;
            this.startValueRow = startValueRow;
            this.startColIndex = columnToIndex(startColumn);
            this.mapperRules = mapperRules;
            this.rowHandler = rowHandler;

            this.mapperEngine = new MapperEngine();
            this.mapperEngine.setMapperRules(this.mapperRules);
        }

        @Override
        public void startRow(int rowNum) {
            currentRowNum = rowNum;
            currentRow.clear();
        }

        @Override
        public void endRow(int rowNum) {
            boolean isHeaderRow = false;
            for (int hr : headerRows) {
                if (hr == rowNum) {
                    isHeaderRow = true;
                    break;
                }
            }

            if (isHeaderRow) {
                for (Map.Entry<Integer, String> entry : currentRow.entrySet()) {
                    if (entry.getKey() >= startColIndex) {
                        headerBuffers.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
                        if (entry.getKey() > maxHeaderCol)
                            maxHeaderCol = entry.getKey();
                    }
                }
            } else if (rowNum >= startValueRow) {
                if (headers.isEmpty()) {
                    buildHeaders();
                }

                List<MapperModel> models = new ArrayList<>();
                boolean hasValue = false;

                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    if (header.isEmpty())
                        continue;

                    String val = currentRow.getOrDefault(startColIndex + i, "");
                    if (!val.isEmpty())
                        hasValue = true;

                    MapperModel model = new MapperModel();
                    model.setKey(header);
                    model.setValue(val);
                    model.setTypeData("string");
                    models.add(model);
                }

                if (hasValue) {
                    MapperObject mapperObject = new MapperObject();
                    mapperObject.convertToObject(models);
                    Map<String, Object> map = mapperObject.buildObject();
                    if (!map.isEmpty()) {
                        try {
                            // Use the pre-initialized MapperEngine
                            List<Map<String, Object>> rows = new ArrayList<>();
                            rows.add(map);
                            mapperEngine.map(rows);

                            Map<String, Object> mappedRow = mapperEngine.get().get(0);
                            rowHandler.handle(mappedRow);
                        } catch (Exception e) {
                            throw new RuntimeException("Error handling row at " + rowNum, e);
                        }
                    }
                }
            }
        }

        private void buildHeaders() {
            for (int c = startColIndex; c <= maxHeaderCol; c++) {
                List<String> parts = headerBuffers.get(c);
                if (parts == null || parts.isEmpty()) {
                    headers.add("");
                } else {
                    headers.add(String.join(".", parts));
                }
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int colNum = new CellReference(cellReference).getCol();
            currentRow.put(colNum, formattedValue != null ? formattedValue.trim() : "");
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
        }
    }

    private static Result processWorkbook(
            Workbook workbook,
            int[] headerRows,
            int startValueRow,
            String startColumn,
            List<MapperRule> mapperRules) throws Exception {

        int startColIndex = columnToIndex(startColumn);
        Sheet sheet = workbook.getSheetAt(0);

        List<Row> headerRowList = new ArrayList<>();
        for (int r : headerRows) {
            Row row = sheet.getRow(r);
            if (row != null)
                headerRowList.add(row);
        }

        if (headerRowList.isEmpty()) {
            throw new IllegalArgumentException("Header row tidak ditemukan");
        }

        List<String> headers = buildHeaders(sheet, headerRowList, startColIndex);
        List<Map<String, Object>> values = extractValues(sheet, headers, startValueRow, startColIndex);

        return new Result(headers, values, mapperRules);
    }

    // ===================== HEADER =====================

    private static List<String> buildHeaders(
            Sheet sheet,
            List<Row> headerRows,

            int startCol) {
        int maxCol = headerRows.stream()
                .mapToInt(Row::getLastCellNum)
                .max()
                .orElse(0);

        List<String> headers = new ArrayList<>();

        Row parentRow = headerRows.get(0);
        Row childRow = headerRows.size() > 1 ? headerRows.get(1) : null;

        for (int c = startCol; c < maxCol; c++) {

            String parent = getCellWithMerge(sheet, parentRow, c);
            boolean verticalMerge = isVerticalMerge(sheet, parentRow.getRowNum(), c);

            String header;

            if (!verticalMerge && childRow != null) {
                String child = getCellWithMerge(sheet, childRow, c);

                if (!parent.isEmpty() && !child.isEmpty()) {
                    header = parent + "." + child;
                } else if (!parent.isEmpty()) {
                    header = parent;
                } else {
                    header = child;
                }
            } else {
                header = parent;
            }

            headers.add(header);
            // headers.add(normalize(header));
        }

        return headers;
    }

    private static String getCellWithMerge(Sheet sheet, Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell != null && cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }

        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(row.getRowNum(), col)) {
                Row firstRow = sheet.getRow(region.getFirstRow());
                if (firstRow != null) {
                    Cell firstCell = firstRow.getCell(region.getFirstColumn(),
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                    if (firstCell != null &&
                            firstCell.getCellType() == CellType.STRING) {
                        return firstCell.getStringCellValue().trim();
                    }
                }
            }
        }
        return "";
    }

    private static boolean isVerticalMerge(Sheet sheet, int row, int col) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(row, col)) {
                return region.getLastRow() > region.getFirstRow();
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? ""
                : value.trim()
                        .toLowerCase()
                        .replaceAll("\\s+", "_");
    }

    // ===================== VALUE =====================

    private static List<Map<String, Object>> extractValues(
            Sheet sheet,
            List<String> headers,
            int startRow,
            int startCol) {
        List<Map<String, Object>> data = new ArrayList<>();

        for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;

            List<MapperModel> models = new ArrayList<>();
            boolean hasValue = false;

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                if (header.isEmpty())
                    continue;

                Cell cell = row.getCell(startCol + i,
                        Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

                CellValueResult result = readCellValueWithType(cell);

                if (!result.value.isEmpty()) {
                    hasValue = true;
                }

                MapperModel model = new MapperModel();
                model.setKey(header);
                model.setValue(result.value);
                model.setTypeData(result.type);

                models.add(model);
            }

            if (hasValue) {
                MapperObject mapperObject = new MapperObject();
                mapperObject.convertToObject(models);
                Map<String, Object> map = mapperObject.buildObject();

                if (!map.isEmpty()) {
                    data.add(map);
                }
            }
        }
        return data;
    }

    // ===================== CELL VALUE =====================

    private static CellValueResult readCellValueWithType(Cell cell) {

        if (cell == null) {
            return new CellValueResult("", "blank");
        }

        return switch (cell.getCellType()) {
            case STRING -> new CellValueResult(cell.getStringCellValue().trim(), "string");
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield new CellValueResult(
                            cell.getLocalDateTimeCellValue()
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            "date");
                } else {
                    yield new CellValueResult(String.valueOf(cell.getNumericCellValue()), "number");
                }
            }
            case BOOLEAN -> new CellValueResult(String.valueOf(cell.getBooleanCellValue()), "boolean");
            case FORMULA -> new CellValueResult(cell.getCellFormula(), "formula");
            default -> new CellValueResult("", "blank");
        };
    }

    static class CellValueResult {
        String value;
        String type;

        CellValueResult(String value, String type) {
            this.value = value;
            this.type = type;
        }
    }

    // ===================== COLUMN UTILS =====================

    private static int columnToIndex(String column) {
        column = column.toUpperCase();
        int index = 0;
        for (char c : column.toCharArray()) {
            index = index * 26 + (c - 'A' + 1);
        }
        return index - 1;
    }

    // ===================== RESULT =====================

    public static class Result {
        public final List<String> headers;
        public final List<Map<String, Object>> values;
        public final List<MapperRule> returnRules;

        public Result(List<String> headers, List<Map<String, Object>> values, List<MapperRule> returnRules) {
            this.headers = headers;
            this.values = values;
            this.returnRules = returnRules;
        }

        public List<Map<String, Object>> get() {
            MapperEngine mapperEngine = new MapperEngine();
            mapperEngine.setMapperRules(this.returnRules);
            mapperEngine.map(this.values);
            return mapperEngine.get();
        }
    }
}
