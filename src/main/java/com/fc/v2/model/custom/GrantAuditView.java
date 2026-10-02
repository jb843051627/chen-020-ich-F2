package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 工坊认定核准单复查视图：省厅认下锁单之后，从省厅倒着数回县里逐档清点。
 * 结论与窗口视图（GrantBillView）出自同一回算——同一份落名台账、同一套点数办法，
 * 不许一处取数、一处手写两套名单。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "GrantAuditView", description = "工坊认定核准单复查视图")
public class GrantAuditView implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "同一回算给出的窗口视图（核准结论与它同出一处）")
    private GrantBillView view;

    @ApiModelProperty(value = "复查是否站得住：已认下、逐档数齐、总笔数与核准名单合拢、纸面与台账对齐")
    private Boolean pass;

    @ApiModelProperty(value = "核准名单总笔数（各档凑齐轮次名单之和）")
    private Integer totalCount;

    @ApiModelProperty(value = "从省厅倒着数回县里的逐档清点")
    private List<NodeAudit> audits;

    /** 一档一核 */
    @ApiModel(value = "GrantAuditView.NodeAudit", description = "逐档清点")
    public static class NodeAudit implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "档口 2省 1市 0县（倒序给）")
        private Integer nodeNo;

        @ApiModelProperty(value = "档口名")
        private String nodeName;

        @ApiModelProperty(value = "该档凑齐时所对的起签轮次")
        private Integer roundNo;

        @ApiModelProperty(value = "按规矩该凑几个人")
        private Integer needCount;

        @ApiModelProperty(value = "核准名单上点出的人数")
        private Integer approvedCount;

        @ApiModelProperty(value = "核准名单")
        private List<String> approvedSigners;

        @ApiModelProperty(value = "单拎这一档核是否站得住")
        private Boolean nodePass;

        public Integer getNodeNo() {
            return nodeNo;
        }

        public void setNodeNo(Integer nodeNo) {
            this.nodeNo = nodeNo;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public Integer getRoundNo() {
            return roundNo;
        }

        public void setRoundNo(Integer roundNo) {
            this.roundNo = roundNo;
        }

        public Integer getNeedCount() {
            return needCount;
        }

        public void setNeedCount(Integer needCount) {
            this.needCount = needCount;
        }

        public Integer getApprovedCount() {
            return approvedCount;
        }

        public void setApprovedCount(Integer approvedCount) {
            this.approvedCount = approvedCount;
        }

        public List<String> getApprovedSigners() {
            return approvedSigners;
        }

        public void setApprovedSigners(List<String> approvedSigners) {
            this.approvedSigners = approvedSigners;
        }

        public Boolean getNodePass() {
            return nodePass;
        }

        public void setNodePass(Boolean nodePass) {
            this.nodePass = nodePass;
        }
    }

    public GrantBillView getView() {
        return view;
    }

    public void setView(GrantBillView view) {
        this.view = view;
    }

    public Boolean getPass() {
        return pass;
    }

    public void setPass(Boolean pass) {
        this.pass = pass;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public List<NodeAudit> getAudits() {
        return audits;
    }

    public void setAudits(List<NodeAudit> audits) {
        this.audits = audits;
    }
}
