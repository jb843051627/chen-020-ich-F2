package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.custom.GrantAuditView;
import com.fc.v2.model.custom.GrantBillView;
import com.fc.v2.service.ITIchGrantBillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 工坊认定核准单 Controller（approval-chain 形状：县—市—省三级审批）。
 * 前端没有第二处能径直改档号的把手：档编号只随落名当意向递上来，认不认全看服务层。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "工坊认定核准单")
@Controller
@RequestMapping("/ichGrantBill")
public class IchGrantBillController extends BaseController {

    private final String prefix = "admin/ichGrantBill";

    @Autowired
    private ITIchGrantBillService ichGrantBillService;

    @ApiOperation(value = "核准台账跳转", notes = "核准台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("ichGrantBill:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "工坊认定核准单台账", action = "list")
    @ApiOperation(value = "认定单台账", notes = "认定单台账")
    @GetMapping("/list")
    @RequiresPermissions("ichGrantBill:list")
    @ResponseBody
    public ResultTable list(TIchGrantBill record) {
        QueryWrapper<TIchGrantBill> queryWrapper = new QueryWrapper<TIchGrantBill>();
        queryWrapper.orderByDesc("create_time", "id");
        startPage();
        com.github.pagehelper.PageInfo<TIchGrantBill> page =
                new com.github.pagehelper.PageInfo<TIchGrantBill>(ichGrantBillService.selectTIchGrantBillList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "工坊认定单起单", action = "open")
    @ApiOperation(value = "新开一张认定单", notes = "落在县文旅档，在核")
    @PostMapping("/open")
    @RequiresPermissions("ichGrantBill:open")
    @ResponseBody
    public AjaxResult open(String billNo, String remark) {
        if (billNo == null || billNo.trim().isEmpty()) {
            return AjaxResult.error("认定单号不能为空");
        }
        TIchGrantBill bill = ichGrantBillService.openBill(billNo.trim(), remark);
        return AjaxResult.success(200, "认定单已开，停在县文旅档", bill);
    }

    @Log(title = "工坊认定落名", action = "sign")
    @ApiOperation(value = "落名（唯一直达服务层的推进入口）",
            notes = "nodeNo 只是界面意向档；越档、锁后补名、重复落名一律按服务层裁定")
    @PostMapping("/sign")
    @RequiresPermissions("ichGrantBill:sign")
    @ResponseBody
    public AjaxResult sign(Long id, Integer nodeNo, String signer, String comment) {
        GrantBillView view = ichGrantBillService.sign(id, nodeNo, signer, comment);
        if (view == null || view.getId() == null) {
            return AjaxResult.error("认定单不存在");
        }
        if (Boolean.TRUE.equals(view.getAccepted())) {
            return AjaxResult.success(200, view.getMessage(), view);
        }
        return AjaxResult.error(view.getMessage()).put("data", view);
    }

    @Log(title = "工坊认定挪回", action = "rollback")
    @ApiOperation(value = "挪回紧挨的上一档", notes = "早先落名一笔不抹，另起一轮重新起签")
    @PostMapping("/rollback")
    @RequiresPermissions("ichGrantBill:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String operator, String comment) {
        GrantBillView view = ichGrantBillService.rollback(id, operator, comment);
        if (view == null || view.getId() == null) {
            return AjaxResult.error("认定单不存在");
        }
        if (Boolean.TRUE.equals(view.getAccepted())) {
            return AjaxResult.success(200, view.getMessage(), view);
        }
        return AjaxResult.error(view.getMessage()).put("data", view);
    }

    @ApiOperation(value = "窗口视图", notes = "一档一处：应到几人、怎么算齐、已落几名、停在第几档")
    @GetMapping("/viewData")
    @RequiresPermissions("ichGrantBill:view")
    @ResponseBody
    public AjaxResult viewData(Long id) {
        GrantBillView view = ichGrantBillService.grantView(id);
        if (view == null || view.getId() == null) {
            return AjaxResult.error("认定单不存在");
        }
        return AjaxResult.successData(200, view);
    }

    @ApiOperation(value = "复查", notes = "从省厅倒着数回县里逐档清点，与窗口同一回算")
    @GetMapping("/audit")
    @RequiresPermissions("ichGrantBill:audit")
    @ResponseBody
    public AjaxResult audit(Long id) {
        GrantAuditView audit = ichGrantBillService.audit(id);
        return AjaxResult.successData(200, audit);
    }

    @ApiOperation(value = "落名台账", notes = "新旧轮次分开摆，逐笔留档")
    @GetMapping("/ledger")
    @RequiresPermissions("ichGrantBill:view")
    @ResponseBody
    public AjaxResult ledger(Long id) {
        return AjaxResult.successData(200, ichGrantBillService.signLedger(id));
    }
}
