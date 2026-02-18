package com.dm.util;

import com.dm.bean.OrderCoilMapping4TruckDetail;
import com.dm.bean.PlanOutput4TruckDetail;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.util.List;

public class ExcelWriter {

    private static final String[] HEADERS = {
        "GROUP_ID", "BATCH_NUMBER", "ORDER_NUMBER", "ORDER_LINE_NUMBER",
        "STOCK_WEIGHT", "STOCK_WIDTH", "STOCK_LENGTH", "LOCATION",
        "TRUCK_TYPE_ID", "TRUCK_NUMBER"
    };

    public void writeTruckOutputPlan(String filePath, 
                                      List<PlanOutput4TruckDetail<Integer>> planOutputDetails) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("TruckTypeWiseOrderCoilMapping");
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Write data
            int rowNum = 1;
            for (PlanOutput4TruckDetail<Integer> outputDetail : planOutputDetails) {
                for (OrderCoilMapping4TruckDetail<Integer> detail : outputDetail.getOrderCoilMappingDetails()) {
                    Row row = sheet.createRow(rowNum++);
                    
                    row.createCell(0).setCellValue(detail.getGroupId());
                    row.createCell(1).setCellValue(detail.getBatchNo() != null ? detail.getBatchNo() : "");
                    row.createCell(2).setCellValue(detail.getOrderNo() != null ? detail.getOrderNo() : "");
                    row.createCell(3).setCellValue(detail.getOrderLineItem() != null ? detail.getOrderLineItem() : "");
                    row.createCell(4).setCellValue(detail.getStockWeight());
                    row.createCell(5).setCellValue(detail.getStockWidth());
                    row.createCell(6).setCellValue(detail.getStockLength());
                    row.createCell(7).setCellValue(detail.getLocation() != null ? detail.getLocation() : "");
                    row.createCell(8).setCellValue(detail.getTruckTypeId());
                    row.createCell(9).setCellValue(detail.getTruckNumber());
                }
            }

            // Auto-size columns
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            
            System.out.println("Excel file written successfully: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error writing Excel file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}