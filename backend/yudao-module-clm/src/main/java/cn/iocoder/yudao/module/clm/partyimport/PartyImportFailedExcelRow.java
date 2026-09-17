package cn.iocoder.yudao.module.clm.partyimport;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyImportFailedExcelRow {
    @ExcelProperty("原 Excel 行号")
    private Integer rowNo;
    @ExcelProperty("相对方名称*")
    private String name;
    @ExcelProperty("统一社会信用代码*")
    private String unifiedCreditCode;
    @ExcelProperty("联系人")
    private String contactName;
    @ExcelProperty("联系电话")
    private String contactPhone;
    @ExcelProperty("处理状态")
    private String status;
    @ExcelProperty("失败原因")
    private String errorMessage;
}
