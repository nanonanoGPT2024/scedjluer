package co.id.mcs.dika.repository;

import co.id.mcs.dika.model.OrderData;
import co.id.mcs.ptdika.MadMachine.Repository.RepositoryDao;
import co.id.mcs.ptdika.MadMachine.Repository.RepositoryJdbc;

@RepositoryJdbc(datasource = "dataSource")
public interface OrderDataRepository extends RepositoryDao<OrderData> {
}
