package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.auto.TIchGrantSign;
import com.fc.v2.model.custom.GrantAuditView;
import com.fc.v2.model.custom.GrantBillView;

import java.util.List;

/**
 * 工坊认定核准单 Service接口（approval-chain 形状：三级审批、同档并签、落名台账）
 *
 * 认定只发生在 {@link #sign} 这一个落名入口里：前端递上来的档编号一律当意向，
 * 算不算、停在哪一档、锁不锁、旧名抹不抹全听它的答复。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITIchGrantBillService {

    /** 按主键回查单据（老方法维持不动，收什么给什么照旧） */
    TIchGrantBill selectTIchGrantBillById(Long id);

    /** 单据台账 */
    List<TIchGrantBill> selectTIchGrantBillList(QueryWrapper<TIchGrantBill> queryWrapper);

    /** 新开一张认定单：落在县文旅档，在核 */
    TIchGrantBill openBill(String billNo, String remark);

    /**
     * 唯一落名入口。intentNode 只是前端意向档，与单据实停档口相左一律按服务层裁的来：
     * 越档先落按没落算、省厅认下后补不进、同档同一同志不重复计数；
     * 凑齐的规矩顺着落名台账逐笔点，不看格子里敲的数。
     * 返回的视图里带 accepted/message 与裁定后的当前档口。
     */
    GrantBillView sign(Long id, Integer intentNode, String signer, String comment);

    /**
     * 挪回：只挪紧挨的上一格，早先各档落名一笔不抹，另起一轮从被退回档重新起签；
     * 已认下锁单的、已在县档无处可退的，被拒。
     */
    GrantBillView rollback(Long id, String operator, String comment);

    /** 窗口视图：一档一处四件事，全部按台账同一回算 */
    GrantBillView grantView(Long id);

    /** 复查视图：从省厅倒着数回县里逐档清点，与窗口同一回算给出核准结论 */
    GrantAuditView audit(Long id);

    /** 落名留档：新旧轮次分开摆，哪一日追补的逐笔可查 */
    List<TIchGrantSign> signLedger(Long id);
}
