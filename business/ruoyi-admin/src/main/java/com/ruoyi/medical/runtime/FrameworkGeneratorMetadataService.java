package com.ruoyi.medical.runtime;

import com.ruoyi.generator.domain.GenTable;
import com.ruoyi.generator.service.GenTableServiceImpl;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Native metadata logic, explicit isolated transaction; never replaces the application's default manager. */
public class FrameworkGeneratorMetadataService extends GenTableServiceImpl {
    private final TransactionTemplate development;
    public FrameworkGeneratorMetadataService(@Qualifier("frameworkDevelopmentDataSource") DataSource source) {
        development = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateGenTable(GenTable table) { development.executeWithoutResult(status -> super.updateGenTable(table)); }
    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteGenTableByIds(Long[] ids) { development.executeWithoutResult(status -> super.deleteGenTableByIds(ids)); }
    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void importGenTable(List<GenTable> tables, String webType, String operator) {
        development.executeWithoutResult(status -> super.importGenTable(tables, webType, operator));
    }
    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void synchDb(String name) { development.executeWithoutResult(status -> super.synchDb(name)); }
}
