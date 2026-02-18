package com.dm.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExcelReader {

    public LinkedHashMap<Integer, LinkedHashMap<String, Object>> getDataFromFile(String dataFilePath,
                                                                                 String sheetName) {
        LinkedHashMap<Integer, LinkedHashMap<String, Object>> datas = new LinkedHashMap<>();
        
        try (FileInputStream file = new FileInputStream(new File(dataFilePath));
             XSSFWorkbook workbook = new XSSFWorkbook(file)) {
            
            XSSFSheet sheet = workbook.getSheet(sheetName);
            
            if (sheet == null) {
                System.err.println("Sheet " + sheetName + " not found in " + dataFilePath);
                return datas;
            }
            
            Iterator<Row> rowIterator = sheet.iterator();
            int cnt = 0;
            Map<Integer, String> header = new LinkedHashMap<>();
            
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                
                if (cnt == 1) {
                    Iterator<Cell> cellIterator = row.cellIterator();
                    int cellCnt = 0;
                    while (cellIterator.hasNext()) {
                        Cell cell = cellIterator.next();
                        header.put(cellCnt, cell.getStringCellValue());
                        cellCnt++;
                    }
                }

                if (cnt >= 2) {
                    LinkedHashMap<String, Object> data = new LinkedHashMap<>();
                    Iterator<Cell> cellIterator = row.cellIterator();
                    int cellCnt = 0;
                    
                    while (cellIterator.hasNext()) {
                        Cell cell = cellIterator.next();
                        String cellValue = getCellValue(cell);
                        
                        if (header.containsKey(cellCnt)) {
                            data.put(header.get(cellCnt), cellValue);
                        }
                        cellCnt++;
                    }
                    
                    if (!data.isEmpty()) {
                        datas.put(cnt, data);
                    }
                }
                cnt++;
            }
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return datas;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        
        CellType cellType = cell.getCellType();
        
        if (cellType == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        } else if (cellType == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return String.valueOf(cell.getDateCellValue());
            } else {
                return String.valueOf(cell.getNumericCellValue());
            }
        } else if (cellType == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cellType == CellType.FORMULA) {
            return cell.getCellFormula();
        } else {
            return "";
        }
    }
}