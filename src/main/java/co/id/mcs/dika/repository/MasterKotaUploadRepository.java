package co.id.mcs.dika.repository;

import co.id.mcs.dika.model.MasterKotaUpload;
import co.id.mcs.ptdika.MadMachine.Repository.RepositoryDao;
import co.id.mcs.ptdika.MadMachine.Repository.RepositoryJdbc;

@RepositoryJdbc(datasource = "dataSource")
public interface MasterKotaUploadRepository extends RepositoryDao<MasterKotaUpload> {
}
