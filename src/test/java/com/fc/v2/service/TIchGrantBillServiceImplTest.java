package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fc.v2.mapper.auto.TIchGrantBillMapper;
import com.fc.v2.mapper.auto.TIchGrantSignMapper;
import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.auto.TIchGrantSign;
import com.fc.v2.model.custom.GrantAuditView;
import com.fc.v2.model.custom.GrantBillView;
import com.fc.v2.service.impl.TIchGrantBillServiceImpl;

/**
 * 工坊认定核准单服务层测试：
 * 三档凑齐算法、越档不认、省批锁死、挪回只一格且旧名不抹、窗口与复查同一回算。
 */
@ExtendWith(MockitoExtension.class)
public class TIchGrantBillServiceImplTest {

    @Mock
    private TIchGrantBillMapper billMapper;

    @Mock
    private TIchGrantSignMapper signMapper;

    @InjectMocks
    private TIchGrantBillServiceImpl service;

    private final Map<Long, TIchGrantBill> bills = new HashMap<Long, TIchGrantBill>();
    private final List<TIchGrantSign> ledger = new ArrayList<TIchGrantSign>();
    private long billSeq = 0;
    private long signSeq = 0;
    private long clock = 1_000_000L;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(service, "ichGrantBillMapper", billMapper);
        ReflectionTestUtils.setField(service, "ichGrantSignMapper", signMapper);

        lenient().when(billMapper.selectById(any())).thenAnswer(inv -> bills.get(inv.getArgument(0)));
        lenient().when(billMapper.insert(any(TIchGrantBill.class))).thenAnswer(inv -> {
            TIchGrantBill b = inv.getArgument(0);
            b.setId(++billSeq);
            bills.put(b.getId(), b);
            return 1;
        });
        lenient().when(billMapper.updateById(any(TIchGrantBill.class))).thenAnswer(inv -> 1);
        lenient().when(signMapper.insert(any(TIchGrantSign.class))).thenAnswer(inv -> {
            TIchGrantSign s = inv.getArgument(0);
            s.setId(++signSeq);
            s.setCreateTime(new Date(clock++));
            ledger.add(s);
            return 1;
        });
        lenient().when(signMapper.selectList(any())).thenAnswer(inv -> new ArrayList<TIchGrantSign>(ledger));
    }

    private Long openBill() {
        return service.openBill("GF-2026-001", "苗绣工坊").getId();
    }

    @Test
    public void testThreeLevelFlow_countyOneCityTwoProvinceOne() {
        Long id = openBill();

        // 县文旅一笔即可：头一人落地即往前
        GrantBillView v1 = service.sign(id, 0, "县里甲", null);
        assertTrue(v1.getAccepted());
        assertEquals(1, v1.getNodeNo(), "县档一人即过，单子到市");
        assertEquals("市文旅", v1.getNodeName());

        // 市文旅两笔点齐：只落一名窗口仍旧候签
        GrantBillView v2 = service.sign(id, 1, "市里甲", null);
        assertTrue(v2.getAccepted());
        assertEquals(1, v2.getNodeNo(), "市里只落一名，仍停市档");
        GrantBillView.NodeState city = v2.getNodes().get(1);
        assertEquals(1, city.getSignedCount());
        assertEquals("候签", city.getStateText());
        assertEquals(2, city.getNeedCount());
        assertFalse(v2.getLocked());

        // 市里第二名点齐，推到省
        GrantBillView v3 = service.sign(id, 1, "市里乙", null);
        assertTrue(v3.getAccepted());
        assertEquals(2, v3.getNodeNo());

        // 省文旅一笔即可，点头即锁
        GrantBillView v4 = service.sign(id, 2, "省里甲", null);
        assertTrue(v4.getAccepted());
        assertEquals(2, v4.getNodeNo());
        assertEquals(1, v4.getStatus());
        assertTrue(v4.getLocked());
        assertTrue(v4.getAligned(), "落名条数与档口数两处对得齐");
        assertEquals(4, v4.getLedgerTotal(), "台账共4笔落名");
    }

    @Test
    public void testSkipLevelSignCountsAsNothing_evenForProvince() {
        Long id = openBill();

        // 单子在县，市里先落：按没落算，不留笔、不挪档
        GrantBillView v = service.sign(id, 1, "市里甲", null);
        assertFalse(v.getAccepted());
        assertEquals(0, v.getNodeNo());
        assertTrue(v.getMessage().contains("按没落算"));
        assertEquals(0, service.signLedger(id).size());

        // 省厅绕过市县径直落名：照样不认
        GrantBillView v2 = service.sign(id, 2, "省里甲", null);
        assertFalse(v2.getAccepted());
        assertEquals(0, v2.getNodeNo());
        assertEquals(0, service.signLedger(id).size());

        // 屏上档号与服务层相左：意向落省、实停县，仍按服务层的来
        GrantBillView v3 = service.sign(id, 2, "县里甲", null);
        assertFalse(v3.getAccepted());
        assertEquals(0, v3.getNodeNo());
    }

    @Test
    public void testAfterProvinceApprovesNobodyCanBeAdded() {
        Long id = openBill();
        service.sign(id, 0, "县里甲", null);
        service.sign(id, 1, "市里甲", null);
        service.sign(id, 1, "市里乙", null);
        service.sign(id, 2, "省里甲", null);

        // 认下之后还补不补得进人：补不进
        GrantBillView again = service.sign(id, 2, "省里乙", null);
        assertFalse(again.getAccepted());
        assertTrue(again.getLocked());
        assertTrue(again.getMessage().contains("锁上"));
        assertEquals(4, service.signLedger(id).size());

        // 锁单后也挪不回
        GrantBillView rb = service.rollback(id, "省里甲", "反悔");
        assertFalse(rb.getAccepted());
        assertEquals(1, service.selectTIchGrantBillById(id).getStatus().intValue());
    }

    @Test
    public void testSameSignerTwiceInOneNodeNotCountedTwice() {
        Long id = openBill();
        service.sign(id, 0, "县里甲", null);
        GrantBillView first = service.sign(id, 1, "市里甲", null);
        assertTrue(first.getAccepted());
        // 同一同志在市档重复落：不重复点，仍候签
        GrantBillView again = service.sign(id, 1, "市里甲", null);
        assertFalse(again.getAccepted());
        assertEquals(1, again.getNodeNo());
        assertEquals(1, again.getNodes().get(1).getSignedCount());
    }

    @Test
    public void testRollbackOneStepOnly_oldSignsKept_newRoundSeparate() {
        Long id = openBill();
        service.sign(id, 0, "县里甲", null);
        service.sign(id, 1, "市里甲", null); // 市里只落一名

        // 市里把单子退回：只挪紧挨的上一格（回县，不是回起点），旧名一笔不抹
        GrantBillView rb = service.rollback(id, "市里甲", "材料不齐");
        assertTrue(rb.getAccepted());
        assertEquals(0, rb.getNodeNo());
        assertEquals(2, rb.getRoundNo(), "另起一轮");
        assertEquals(2, rb.getStatus(), "情形为已挪回");

        // 早先两笔落名 + 一笔挪回都在台账里，新旧分开摆
        List<TIchGrantSign> rows = service.signLedger(id);
        assertEquals(3, rows.size());
        assertEquals(TIchGrantSign.ACTION_ROLLBACK, rows.get(2).getAction());
        assertEquals(1, rows.get(2).getNodeNo(), "挪回笔记在退单的市档");
        assertEquals(1, rows.get(2).getRoundNo(), "挪回发生在第1轮");

        // 县档窗口第2轮重新起签：旧轮县名仍在台账，但本轮已落0
        GrantBillView w = service.grantView(id);
        assertEquals(0, w.getNodes().get(0).getSignedCount());
        assertEquals(0, service.selectTIchGrantBillById(id).getSignCount().intValue());

        // 从被退回的县档往上续：县1笔、市2笔（市里甲上一轮的旧名不算，需重落）、省1笔
        assertTrue(service.sign(id, 0, "县里甲", null).getAccepted());
        assertEquals(0, service.selectTIchGrantBillById(id).getStatus().intValue(), "重新起签后回到在核");
        assertTrue(service.sign(id, 1, "市里甲", null).getAccepted());
        assertTrue(service.sign(id, 1, "市里乙", null).getAccepted());
        assertTrue(service.sign(id, 2, "省里甲", null).getAccepted());

        TIchGrantBill bill = service.selectTIchGrantBillById(id);
        assertEquals(1, bill.getStatus().intValue());
        assertEquals(2, bill.getRoundNo().intValue());

        // 复查：总笔数与核准名单合拢，新轮名单4人；旧轮名字在台账留档却不进核准名单
        GrantAuditView audit = service.audit(id);
        assertTrue(audit.getPass());
        assertEquals(4, audit.getTotalCount());
        // 倒序：省、市、县
        assertEquals(2, audit.getAudits().get(0).getNodeNo().intValue());
        assertEquals(0, audit.getAudits().get(2).getNodeNo().intValue());
        for (GrantAuditView.NodeAudit a : audit.getAudits()) {
            assertTrue(a.getNodePass(), "随便拎出哪一档单独核都站得住");
            assertEquals(2, a.getRoundNo().intValue(), "核准名单取自最后凑齐的第2轮");
        }
        assertEquals(2, audit.getAudits().get(1).getApprovedCount().intValue());
        assertTrue(audit.getAudits().get(1).getApprovedSigners().contains("市里乙"));
        assertEquals(7, service.signLedger(id).size(), "台账共6笔落名+1笔挪回，旧名一笔不抹");
    }

    @Test
    public void testRollbackFromProvinceGoesToCityNotCounty() {
        Long id = openBill();
        service.sign(id, 0, "县里甲", null);
        service.sign(id, 1, "市里甲", null);
        service.sign(id, 1, "市里乙", null);
        // 单到省档但还没点（省未认），此时省档退回
        GrantBillView rb = service.rollback(id, "省里甲", "再议");
        assertTrue(rb.getAccepted());
        assertEquals(1, rb.getNodeNo(), "只挪一格，回到市，不回县");
        assertEquals(2, rb.getRoundNo());
    }

    @Test
    public void testRollbackAtCountyRejected() {
        Long id = openBill();
        GrantBillView rb = service.rollback(id, "县里甲", "无处可退");
        assertFalse(rb.getAccepted());
        assertEquals(0, service.selectTIchGrantBillById(id).getNodeNo().intValue());
    }

    @Test
    public void testAuditFailsBeforeFinalApprove() {
        Long id = openBill();
        service.sign(id, 0, "县里甲", null);
        service.sign(id, 1, "市里甲", null);

        GrantAuditView audit = service.audit(id);
        assertFalse(audit.getPass(), "没认下、市省没凑齐，复查站不住");
        assertFalse(audit.getAudits().get(0).getNodePass(), "省档空，单档也不合");
        assertNotNull(audit.getView(), "复查与窗口同出一回算");
    }

    @Test
    public void testOldQueryMethodUnchangedAndMissingBill() {
        // 老方法维持不动：收什么给什么照旧
        assertNull(service.selectTIchGrantBillById(999L));
        GrantBillView v = service.sign(999L, 0, "甲", null);
        assertFalse(v.getAccepted());
        GrantAuditView a = service.audit(999L);
        assertFalse(a.getPass());
    }
}
