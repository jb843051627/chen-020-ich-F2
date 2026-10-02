package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIchGrantBillMapper;
import com.fc.v2.mapper.auto.TIchGrantSignMapper;
import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.auto.TIchGrantSign;
import com.fc.v2.model.custom.GrantAuditView;
import com.fc.v2.model.custom.GrantBillView;
import com.fc.v2.service.ITIchGrantBillService;

/**
 * 工坊认定核准单 Service业务层（approval-chain 形状：县—市—省三级审批）。
 *
 * 定法：
 * 1. 认定只在 {@link #sign} 一个落名入口里发生。前端递来的档编号一律当意向，
 *    越档先落（含省厅绕过市里径直落名）按没落算；窗口档号与入口答复相左，按入口的来。
 * 2. 只能一档一档往前推。三档凑齐算法各有定法：县文旅一笔即可（头一人落地即过），
 *    市文旅两笔点齐（两名同志都落才算，只落一名窗口仍显示候签），省文旅一笔即可。
 *    凑没凑齐不由格子里敲数，顺落名台账逐笔点；同一档同一同志一轮内只点一次。
 * 3. 挪回只挪紧挨的上一格。早先各档落名一笔不抹，另起一轮从被退回档往上续；
 *    台账逐笔留档，新旧轮次分开摆。
 * 4. 省厅点头即锁死，谁也补不进名。
 * 5. 窗口核准结论与复查核准名单同一回算给出，不搞一处取数一处手写两套。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIchGrantBillServiceImpl implements ITIchGrantBillService {

    /** 档口编号：0县文旅 1市文旅 2省文旅 */
    private static final int NODE_COUNTY = 0;
    private static final int NODE_CITY = 1;
    private static final int NODE_PROVINCE = 2;
    private static final int MAX_NODE = NODE_PROVINCE;

    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_RETURNED = 2;

    /** 各档应到人数：县1、市2、省1——三档算法各有各的定法 */
    private static final int[] NODE_NEED = {1, 2, 1};
    /** 各档并签办法：0一笔即可 1两笔点齐 */
    private static final int[] NODE_MODE = {MODE_OR, MODE_AND, MODE_OR};
    private static final String[] NODE_NAMES = {"县文旅", "市文旅", "省文旅"};
    private static final String[] MODE_TEXT = {"一笔即可", "两笔点齐"};

    @javax.annotation.Resource
    private TIchGrantBillMapper ichGrantBillMapper;

    @javax.annotation.Resource
    private TIchGrantSignMapper ichGrantSignMapper;

    @Override
    public TIchGrantBill selectTIchGrantBillById(Long id) {
        return this.ichGrantBillMapper.selectById(id);
    }

    @Override
    public List<TIchGrantBill> selectTIchGrantBillList(QueryWrapper<TIchGrantBill> queryWrapper) {
        return this.ichGrantBillMapper.selectList(queryWrapper);
    }

    @Override
    public TIchGrantBill openBill(String billNo, String remark) {
        TIchGrantBill bill = new TIchGrantBill();
        bill.setBillNo(billNo);
        bill.setNodeNo(NODE_COUNTY);
        bill.setRoundNo(1);
        bill.setSignMode(NODE_MODE[NODE_COUNTY]);
        bill.setNeedCount(NODE_NEED[NODE_COUNTY]);
        bill.setSignCount(0);
        bill.setStatus(STATUS_RUNNING);
        bill.setDelFlag(0);
        bill.setRemark(remark);
        this.ichGrantBillMapper.insert(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantBillView sign(Long id, Integer intentNode, String signer, String comment) {
        TIchGrantBill bill = this.ichGrantBillMapper.selectById(id);
        if (bill == null) {
            return rejectView(null, "认定单不存在");
        }
        initBill(bill);
        if (signer == null || signer.trim().isEmpty()) {
            return buildView(bill, false, "落名同志不能为空");
        }
        signer = signer.trim();

        // 省厅认下即锁死，谁也别想再补一个名
        if (bill.getStatus() != null && bill.getStatus() == STATUS_PASS) {
            return buildView(bill, false, "省厅已认下，单子锁上，补不进名");
        }

        int curNode = bill.getNodeNo();
        int round = bill.getRoundNo();

        // 前端递上来的档编号一律当意向：与单据实停档口相左（越档先落）按没落算，一笔不留
        if (intentNode != null && intentNode.intValue() != curNode) {
            return buildView(bill, false,
                    "单子此刻停在" + NODE_NAMES[curNode] + "，" + NODE_NAMES[safeNode(intentNode)]
                            + "这一笔按没落算（只能一档一档往前推）");
        }

        // 凑没凑齐只顺台账点：同一档同一同志一轮内不重复计数
        Set<String> signed = signersOf(id, round, curNode);
        if (signed.contains(signer)) {
            return buildView(bill, false, signer + "已在本档本轮落过名，不重复点");
        }

        TIchGrantSign row = newRow(bill, curNode, round, TIchGrantSign.ACTION_SIGN, signer, comment);
        this.ichGrantSignMapper.insert(row);

        LinkedHashSet<String> now = new LinkedHashSet<String>(signed);
        now.add(signer);
        boolean full = now.size() >= needOf(curNode);

        if (full) {
            if (curNode == MAX_NODE) {
                // 省厅点头：就此锁上
                bill.setStatus(STATUS_PASS);
            } else {
                bill.setNodeNo(curNode + 1);
                bill.setStatus(STATUS_RUNNING);
            }
        } else if (bill.getStatus() != null && bill.getStatus() == STATUS_RETURNED) {
            // 挪回后重新起签，第一笔有效落名即回到在核
            bill.setStatus(STATUS_RUNNING);
        }
        syncReference(bill);
        this.ichGrantBillMapper.updateById(bill);

        String msg;
        if (full) {
            msg = curNode == MAX_NODE
                    ? NODE_NAMES[curNode] + "凑齐，省厅认下，单子锁上"
                    : NODE_NAMES[curNode] + "凑齐，单子推到" + NODE_NAMES[curNode + 1];
        } else {
            msg = NODE_NAMES[curNode] + "已落" + now.size() + "笔，该档应到" + needOf(curNode)
                    + "笔，仍候签";
        }
        return buildView(bill, true, msg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantBillView rollback(Long id, String operator, String comment) {
        TIchGrantBill bill = this.ichGrantBillMapper.selectById(id);
        if (bill == null) {
            return rejectView(null, "认定单不存在");
        }
        initBill(bill);
        if (bill.getStatus() != null && bill.getStatus() == STATUS_PASS) {
            return buildView(bill, false, "省厅已认下，单子锁上，挪不回去");
        }
        int curNode = bill.getNodeNo();
        int round = bill.getRoundNo();
        if (curNode == NODE_COUNTY) {
            return buildView(bill, false, "已在县文旅档，没有更上一档可退");
        }

        // 哪一档把单子退回去，在该档留一笔挪回；早先各档落名一笔不抹
        TIchGrantSign row = newRow(bill, curNode, round, TIchGrantSign.ACTION_ROLLBACK,
                operator == null || operator.trim().isEmpty() ? "系统" : operator.trim(), comment);
        this.ichGrantSignMapper.insert(row);

        // 只挪紧挨的上一格；重新起签从被退回那一档往上续，新旧两份分开摆
        bill.setNodeNo(curNode - 1);
        bill.setRoundNo(round + 1);
        bill.setStatus(STATUS_RETURNED);
        syncReference(bill);
        this.ichGrantBillMapper.updateById(bill);

        return buildView(bill, true,
                NODE_NAMES[curNode] + "把单子退回" + NODE_NAMES[curNode - 1]
                        + "，早先落名一笔不抹，第" + (round + 1) + "轮重新起签");
    }

    @Override
    public GrantBillView grantView(Long id) {
        TIchGrantBill bill = this.ichGrantBillMapper.selectById(id);
        if (bill == null) {
            return rejectView(id, "认定单不存在");
        }
        initBill(bill);
        return buildView(bill, null, null);
    }

    @Override
    public GrantAuditView audit(Long id) {
        TIchGrantBill bill = this.ichGrantBillMapper.selectById(id);
        GrantAuditView auditView = new GrantAuditView();
        if (bill == null) {
            auditView.setPass(false);
            auditView.setTotalCount(0);
            auditView.setAudits(Collections.<GrantAuditView.NodeAudit>emptyList());
            return auditView;
        }
        initBill(bill);

        GrantBillView view = buildView(bill, null, null);
        auditView.setView(view);

        // 同一回算：顺着台账把单据从头走一遍，记下每档凑齐时所对轮次与名单
        List<TIchGrantSign> ledger = loadLedger(id);
        Map<Integer, NodeFinish> finishes = replay(ledger);

        List<GrantAuditView.NodeAudit> audits = new ArrayList<GrantAuditView.NodeAudit>();
        int total = 0;
        boolean allPass = bill.getStatus() != null && bill.getStatus() == STATUS_PASS
                && view.getNodeNo() == MAX_NODE;
        for (int node = MAX_NODE; node >= NODE_COUNTY; node--) {
            GrantAuditView.NodeAudit a = new GrantAuditView.NodeAudit();
            a.setNodeNo(node);
            a.setNodeName(NODE_NAMES[node]);
            a.setNeedCount(needOf(node));
            NodeFinish f = finishes.get(node);
            List<String> names = f == null ? Collections.<String>emptyList() : f.signers;
            a.setRoundNo(f == null ? null : f.round);
            a.setApprovedSigners(names);
            a.setApprovedCount(names.size());
            boolean nodePass = f != null && names.size() == needOf(node)
                    && new LinkedHashSet<String>(names).size() == names.size();
            a.setNodePass(nodePass);
            allPass = allPass && nodePass;
            total += names.size();
            audits.add(a);
        }
        auditView.setAudits(audits);
        auditView.setTotalCount(total);
        auditView.setPass(allPass);
        return auditView;
    }

    @Override
    public List<TIchGrantSign> signLedger(Long id) {
        return loadLedger(id);
    }

    // ------------------------------------------------------------------
    // 以下都是同一套回算：窗口视图、复查名单全从这里出，不另起第二套取数
    // ------------------------------------------------------------------

    /** 某单某轮某档本轮已落名（按落笔先后去重） */
    private LinkedHashSet<String> signersOf(Long billId, int round, int node) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (TIchGrantSign row : loadLedger(billId)) {
            if (row.getAction() != null && row.getAction() == TIchGrantSign.ACTION_SIGN
                    && val(row.getRoundNo()) == round && val(row.getNodeNo()) == node) {
                names.add(row.getSigner());
            }
        }
        return names;
    }

    private List<TIchGrantSign> loadLedger(Long billId) {
        QueryWrapper<TIchGrantSign> qw = new QueryWrapper<TIchGrantSign>();
        qw.eq("bill_id", billId).orderByAsc("create_time", "id");
        return this.ichGrantSignMapper.selectList(qw);
    }

    /**
     * 顺台账从头走一遍：每档凑齐那一刻记下轮次与核准名单。
     * 挪回后从被退回档往上的各档都要重新凑齐，旧的凑齐留影撤下，
     * 所以认下那一刻各档留影正是最后一轮核准名单（早先旧名仍在台账里一笔不抹）。
     * 越档/重复的记录（理论上入口不留）在此同样点不进去，回算与入口同一把尺子。
     */
    private Map<Integer, NodeFinish> replay(List<TIchGrantSign> ledger) {
        Map<Integer, NodeFinish> finishes = new LinkedHashMap<Integer, NodeFinish>();
        int node = NODE_COUNTY;
        int round = 1;
        Map<Long, Set<String>> have = new LinkedHashMap<Long, Set<String>>();
        for (TIchGrantSign row : ledger) {
            int r = val(row.getRoundNo());
            int n = val(row.getNodeNo());
            if (row.getAction() != null && row.getAction() == TIchGrantSign.ACTION_ROLLBACK) {
                // 挪回笔：退格发生在退单笔之后，只认与当时所在档相符的那一笔
                if (n == node && r == round && node > NODE_COUNTY) {
                    // 从被退回那一档（node-1）往上都要重新起签，旧留影撤下
                    for (int clear = node - 1; clear <= MAX_NODE; clear++) {
                        finishes.remove(clear);
                    }
                    node--;
                    round++;
                }
                continue;
            }
            if (row.getAction() == null || row.getAction() != TIchGrantSign.ACTION_SIGN) {
                continue;
            }
            if (n != node || r != round) {
                continue; // 越档、旧轮的落名不参与当前推进
            }
            Long key = Long.valueOf(((long) r << 8) | n);
            Set<String> set = have.get(key);
            if (set == null) {
                set = new LinkedHashSet<String>();
                have.put(key, set);
            }
            if (set.add(row.getSigner()) && set.size() >= needOf(node)) {
                finishes.put(node, new NodeFinish(round, new ArrayList<String>(set)));
                if (node < MAX_NODE) {
                    node++;
                }
            }
        }
        return finishes;
    }

    private GrantBillView buildView(TIchGrantBill bill, Boolean accepted, String message) {
        GrantBillView v = new GrantBillView();
        v.setId(bill.getId());
        v.setBillNo(bill.getBillNo());
        v.setNodeNo(bill.getNodeNo());
        v.setNodeName(NODE_NAMES[bill.getNodeNo()]);
        v.setRoundNo(bill.getRoundNo());
        v.setStatus(bill.getStatus());
        boolean locked = bill.getStatus() != null && bill.getStatus() == STATUS_PASS;
        v.setLocked(locked);
        v.setAccepted(accepted);
        v.setMessage(message);

        List<TIchGrantSign> ledger = loadLedger(bill.getId());
        int ledgerTotal = 0;
        for (TIchGrantSign row : ledger) {
            if (row.getAction() != null && row.getAction() == TIchGrantSign.ACTION_SIGN) {
                ledgerTotal++;
            }
        }
        v.setLedgerTotal(ledgerTotal);

        Map<Integer, NodeFinish> finishes = replay(ledger);
        int round = bill.getRoundNo();
        int curNode = bill.getNodeNo();

        List<GrantBillView.NodeState> nodes = new ArrayList<GrantBillView.NodeState>();
        boolean aligned = true;
        for (int n = NODE_COUNTY; n <= MAX_NODE; n++) {
            GrantBillView.NodeState st = new GrantBillView.NodeState();
            st.setNodeNo(n);
            st.setNodeName(NODE_NAMES[n]);
            st.setSignMode(NODE_MODE[n]);
            st.setSignModeText(MODE_TEXT[NODE_MODE[n]]);
            st.setNeedCount(needOf(n));
            st.setCurrent(n == curNode);

            List<String> names;
            String stateText;
            if (n < curNode || locked) {
                // 已走过的档：摆该档凑齐那一轮的核准名单（早先旧轮名在台账里另摆）
                NodeFinish f = finishes.get(n);
                names = f == null ? Collections.<String>emptyList() : f.signers;
                stateText = f == null ? "候签" : "已认";
            } else if (n == curNode) {
                names = new ArrayList<String>(signersOf(bill.getId(), round, n));
                stateText = names.size() >= needOf(n) ? "凑齐" : "候签";
            } else {
                names = Collections.emptyList();
                stateText = "候签";
            }
            st.setSigners(names);
            st.setSignedCount(names.size());
            st.setStateText(stateText);
            // 落名条数与档口数两处对得齐：任一档本轮/核准名单都不能点出多于应到的人
            if (names.size() > needOf(n)) {
                aligned = false;
            }
            nodes.add(st);
        }
        // 纸面格子里的参考数也得与台账回算对得齐
        GrantBillView.NodeState cur = nodes.get(curNode);
        aligned = aligned
                && (bill.getSignCount() == null || bill.getSignCount().equals(cur.getSignedCount()))
                && (bill.getNeedCount() == null || bill.getNeedCount().equals(needOf(curNode)));
        v.setAligned(aligned);
        v.setNodes(nodes);
        return v;
    }

    private TIchGrantSign newRow(TIchGrantBill bill, int node, int round, int action,
                                 String signer, String comment) {
        TIchGrantSign row = new TIchGrantSign();
        row.setBillId(bill.getId());
        row.setBillNo(bill.getBillNo());
        row.setNodeNo(node);
        row.setRoundNo(round);
        row.setAction(action);
        row.setSigner(signer);
        row.setSignComment(comment);
        row.setDelFlag(0);
        return row;
    }

    /** 纸面参考列与台账回算同步：格子里的数只当参考，源头仍是台账 */
    private void syncReference(TIchGrantBill bill) {
        int node = bill.getNodeNo();
        bill.setSignMode(NODE_MODE[node]);
        bill.setNeedCount(NODE_NEED[node]);
        bill.setSignCount(signersOf(bill.getId(), bill.getRoundNo(), node).size());
    }

    private void initBill(TIchGrantBill bill) {
        if (bill.getNodeNo() == null) {
            bill.setNodeNo(NODE_COUNTY);
        }
        if (bill.getRoundNo() == null) {
            bill.setRoundNo(1);
        }
        if (bill.getStatus() == null) {
            bill.setStatus(STATUS_RUNNING);
        }
    }

    private int needOf(int node) {
        return NODE_NEED[node];
    }

    private int val(Integer v) {
        return v == null ? 0 : v;
    }

    private int safeNode(Integer node) {
        if (node == null || node < NODE_COUNTY || node > MAX_NODE) {
            return NODE_COUNTY;
        }
        return node;
    }

    private GrantBillView rejectView(Long id, String message) {
        GrantBillView v = new GrantBillView();
        v.setId(id);
        v.setAccepted(false);
        v.setMessage(message);
        v.setAligned(false);
        v.setLocked(false);
        v.setLedgerTotal(0);
        v.setNodes(Collections.<GrantBillView.NodeState>emptyList());
        return v;
    }

    /** 一档凑齐时的留影：所对轮次与按落笔先后的核准名单 */
    private static class NodeFinish {
        final int round;
        final List<String> signers;

        NodeFinish(int round, List<String> signers) {
            this.round = round;
            this.signers = signers;
        }
    }
}
