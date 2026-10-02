package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 工坊认定核准单窗口视图：一档一处看得见四件事——
 * 该档该凑几个人、怎么才算凑齐、眼下落了几个名、整张单停在第几档。
 * 全部数目顺着落名台账逐笔回算，格子里的 signCount/needCount 只当纸面参考。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "GrantBillView", description = "工坊认定核准单窗口视图")
public class GrantBillView implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "认定单主键")
    private Long id;

    @ApiModelProperty(value = "认定单单号")
    private String billNo;

    /** 服务层回算后认下的当前档口，前端递来的档号与此相左一概按此为准 */
    @ApiModelProperty(value = "当前停在第几档（服务层裁定）0县 1市 2省")
    private Integer nodeNo;

    @ApiModelProperty(value = "当前所在档口名")
    private String nodeName;

    @ApiModelProperty(value = "起签轮次（挪回一回加一轮）")
    private Integer roundNo;

    @ApiModelProperty(value = "核准情形 0在核 1已认下 2已挪回")
    private Integer status;

    @ApiModelProperty(value = "是否已锁（省厅认下后谁也补不进名）")
    private Boolean locked;

    @ApiModelProperty(value = "落名条数与档口数两处是否对得齐")
    private Boolean aligned;

    @ApiModelProperty(value = "落名台账总笔数（各轮合计）")
    private Integer ledgerTotal;

    @ApiModelProperty(value = "本次落名是否被认（只在落名入口答复里有值）")
    private Boolean accepted;

    @ApiModelProperty(value = "服务层裁定回话（越档按没落算/已锁/重复落名等）")
    private String message;

    @ApiModelProperty(value = "三档一档一处")
    private List<NodeState> nodes;

    /** 一档一处 */
    @ApiModel(value = "GrantBillView.NodeState", description = "档口状态")
    public static class NodeState implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "档口 0县 1市 2省")
        private Integer nodeNo;

        @ApiModelProperty(value = "档口名")
        private String nodeName;

        @ApiModelProperty(value = "并签办法 0一笔即可 1两笔点齐")
        private Integer signMode;

        @ApiModelProperty(value = "并签办法文字")
        private String signModeText;

        @ApiModelProperty(value = "按规矩该凑几个人")
        private Integer needCount;

        @ApiModelProperty(value = "眼下已落几个名（本轮，去重）")
        private Integer signedCount;

        @ApiModelProperty(value = "本档已落名同志（本轮）")
        private List<String> signers;

        @ApiModelProperty(value = "怎么才算凑齐/眼下情形：候签、凑齐、已认")
        private String stateText;

        @ApiModelProperty(value = "是否当前单据停在的那一档")
        private Boolean current;

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

        public Integer getSignMode() {
            return signMode;
        }

        public void setSignMode(Integer signMode) {
            this.signMode = signMode;
        }

        public String getSignModeText() {
            return signModeText;
        }

        public void setSignModeText(String signModeText) {
            this.signModeText = signModeText;
        }

        public Integer getNeedCount() {
            return needCount;
        }

        public void setNeedCount(Integer needCount) {
            this.needCount = needCount;
        }

        public Integer getSignedCount() {
            return signedCount;
        }

        public void setSignedCount(Integer signedCount) {
            this.signedCount = signedCount;
        }

        public List<String> getSigners() {
            return signers;
        }

        public void setSigners(List<String> signers) {
            this.signers = signers;
        }

        public String getStateText() {
            return stateText;
        }

        public void setStateText(String stateText) {
            this.stateText = stateText;
        }

        public Boolean getCurrent() {
            return current;
        }

        public void setCurrent(Boolean current) {
            this.current = current;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public Boolean getAligned() {
        return aligned;
    }

    public void setAligned(Boolean aligned) {
        this.aligned = aligned;
    }

    public Integer getLedgerTotal() {
        return ledgerTotal;
    }

    public void setLedgerTotal(Integer ledgerTotal) {
        this.ledgerTotal = ledgerTotal;
    }

    public Boolean getAccepted() {
        return accepted;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<NodeState> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeState> nodes) {
        this.nodes = nodes;
    }
}
