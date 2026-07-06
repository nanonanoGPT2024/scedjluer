package co.id.mcs.dika.repository;

import co.id.mcs.dika.model.CustomerAddress;
import co.id.mcs.ptdika.MadMachine.Repository.RepositoryDao;
import co.id.mcs.ptdika.MadMachine.Repository.RepositoryJdbc;

@RepositoryJdbc(datasource = "dataSource")
public interface CustomerAddressRepository extends RepositoryDao<CustomerAddress> {
}
