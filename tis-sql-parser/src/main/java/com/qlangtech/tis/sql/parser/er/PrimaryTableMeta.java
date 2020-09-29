/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.qlangtech.tis.sql.parser.er;

import com.qlangtech.tis.sql.parser.meta.PrimaryLinkKey;
import com.qlangtech.tis.sql.parser.meta.TabExtraMeta;
import com.qlangtech.tis.sql.parser.stream.generate.FlatTableRelation;
import com.qlangtech.tis.sql.parser.tuple.creator.EntityName;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 主索引表配置
 *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2020/04/13
 */
public class PrimaryTableMeta extends TableMeta {

    // 主索引表的主键
    private final List<PrimaryLinkKey> primaryKeyNames;

    public PrimaryTableMeta(String tabName, TabExtraMeta tabExtraMeta) // List<PrimaryLinkKey> primaryKeyName
    {
        super(tabName, tabExtraMeta.getSharedKey());
        this.primaryKeyNames = tabExtraMeta.getPrimaryIndexColumnNames();
    }

    public List<PrimaryLinkKey> getPrimaryKeyNames() {
        if (primaryKeyNames == null) {
            throw new IllegalStateException("primaryKeyNames can not be null");
        }
        long pkCount = this.primaryKeyNames.stream().filter((r) -> r.isPk()).count();
        if (pkCount != 1) {
            throw new IllegalStateException("db pk shall be just one,but current is:" + pkCount);
        }
        return this.primaryKeyNames;
    }

    /**
     * 取得数据库中为物理主键的PK键，其他的非Pk的作为查询Corbar数据源的路由键使用
     *
     * @return
     */
    public PrimaryLinkKey getDBPrimayKeyName() {
        return getPrimaryKeyNames().stream().filter((r) -> r.isPk()).findFirst().get();
    }

    /**
     * 除去主键之外的RouterKeys
     *
     * @return
     */
    public List<PrimaryLinkKey> getPayloadRouterKeys() {
        return getPrimaryKeyNames().stream().filter((r) -> !r.isPk()).collect(Collectors.toList());
    }

    public StringBuffer createPKPlayloadParams(FlatTableRelation... tabRels) {
        return createPKPlayloadParams("row", tabRels);
    }

    /**
     * 创建 CompsitePK，中pload 路由参数
     *
     * @param tabRels
     * @return
     */
    public StringBuffer createPKPlayloadParams(String valToken, FlatTableRelation... tabRels) {
        List<PrimaryLinkKey> payloadRouterKeys = this.getPayloadRouterKeys();
        StringBuffer buffer = new StringBuffer();
        for (PrimaryLinkKey routerKey : payloadRouterKeys) {
            TableRelation.FinalLinkKey finalLinkKey = FlatTableRelation.getFinalLinkKey(routerKey.getName(), tabRels);
            buffer.append(",\"").append(routerKey.getName()).append("\"," + valToken + ".getColumn(\"").append(finalLinkKey.linkKeyName).append("\")");
        }
        return buffer;
    }

    public String createCompositePK(String colTransferToken, String valToken, FlatTableRelation... tabRels) {
        return createCompositePK(colTransferToken, valToken, false, tabRels);
    }

    public String createCompositePK(String colTransferToken, String valToken, boolean force,FlatTableRelation... tabRels) {
        if (tabRels.length > 1) {
            EntityName first = tabRels[0].getHeaderEntity();
            if (!StringUtils.equals(first.getTabName(), this.getTabName())) {
                throw new IllegalArgumentException("first table name shall be '" + this.getTabName() + "' but now is '" + first + "'");
            }
            for (int i = 0; (i + 1) < tabRels.length; i++) {
                if (!tabRels[i].isLinkable(tabRels[i + 1])) {
                    throw new IllegalStateException("pre:" + tabRels[i] + "\nnext:" + tabRels[i + 1] + " is not linkable");
                }
            }
        }

        PrimaryLinkKey pk = this.getDBPrimayKeyName();

        TableRelation.FinalLinkKey finalLinkKey = FlatTableRelation.getFinalLinkKey(pk.getName(), tabRels);
        if (force || finalLinkKey.success) {
            String pkGetterLiteria = EntityName.createColValLiteria(colTransferToken, finalLinkKey.linkKeyName, valToken);

            return " new CompositePK(" + pkGetterLiteria + " " + this.createPKPlayloadParams(valToken, tabRels).toString() + ")";
        } else {
//            // 例如：orderDetail是主表，以order_id作为pk，外表totalpayinfo 为外表（连接键为: totalpay_id -> totalpay_id,所以连接过程会中断
//            if (tabRels.length > 1) {
//                throw new IllegalStateException("linkKeyName:" + finalLinkKey.linkKeyName + ",tabRels size " + tabRels.length + " can not large than 1");
//            }
//            return finalLinkKey.interruptedTableRelation.createSelectParentByChild(context, , , this);
            throw new IllegalStateException("header:" + finalLinkKey.interruptedTableRelation.getHeaderEntity() + ",tailer:" + finalLinkKey.interruptedTableRelation.getTailerEntity()
                    + " can not find key:" + pk.getName() + ",cols:"
                    + finalLinkKey.interruptedTableRelation.getHeaderKeys().stream().map((r) -> "[" + r.getHeadLinkKey() + "->" + r.getTailerLinkKey() + "]")
                    .collect(Collectors.joining(",")));
        }

    }

    public String createCompositePK(FlatTableRelation... tabRels) {
        return createCompositePK("columnMeta", "row", tabRels);
    }

    @Override
    public String toString() {
        return "PrimaryTableMeta{" + "tabName='" + this.getTabName() + '\'' + ", primaryKeyName='" + primaryKeyNames + '\'' + '}';
    }
}
