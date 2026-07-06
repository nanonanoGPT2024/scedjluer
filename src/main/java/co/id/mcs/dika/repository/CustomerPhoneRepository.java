package co.id.mcs.dika.repository;

import co.id.mcs.dika.model.CustomerPhone;
import co.id.mcs.ptdika.MadMachine.Repository.RepositoryDao;
import co.id.mcs.ptdika.MadMachine.Repository.RepositoryJdbc;

@RepositoryJdbc(datasource = "dataSource")
public interface CustomerPhoneRepository extends RepositoryDao<CustomerPhone> {
}
