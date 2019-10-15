package ex;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.springframework.transaction.annotation.Transactional;

public class JPAI_Sample {

    EntityManager em;

    @Transactional
    private void writeData() {
    }

    @Transactional
    public void writeGoodData() {
    }

    private void badWrite() {
        writeGoodData();
    }

    public void ignoreMergeResult(MyEntity e, SubEntity s) {

        em.merge(e);
        em.merge(s);

        List<SubEntity> ss = new ArrayList<SubEntity>();
        ss.add(s);
        e.setSubEntities(ss);

        em.flush();
    }

    @Transactional
    public void noRollbacks(MyEntity e) throws IOException {

    }

    @Transactional(rollbackFor = { SQLException.class,
            CloneNotSupportedException.class }, noRollbackFor = IOException.class)
    public void noDeclaredRollbackExceptions() {
    }

    @Transactional(rollbackFor = SQLException.class, noRollbackFor = IOException.class)
    public void fpDefinedRollBack(MyEntity e) throws SQLException, FileNotFoundException {
    }

    @Transactional(readOnly = true)
    public void fpReadOnlyExceptions(MyEntity e) throws IOException {
    }

    @Entity
    @Table(name = "MY_ENTITY")
    public static class MyEntity {

        private Integer id;
        private List<SubEntity> subEntities;

        @Id
        @SequenceGenerator(name = "MY_ENTITY_SEQ", sequenceName = "MY_ENTITY_SEQ", allocationSize = 100)
        @GeneratedValue(generator = "MY_ENTITY_SEQ")
        @Column(name = "ID", nullable = false, unique = true)
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "myEntity", targetEntity = MyEntity.class)
        public List<SubEntity> getSubEntities() {
            return subEntities;
        }

        public void setSubEntities(List<SubEntity> subEntities) {
            this.subEntities = subEntities;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MyEntity)) {
                return false;
            }

            MyEntity that = (MyEntity) o;
            if (id == null) {
                return that.id == null;
            }

            return id.equals(that.id);
        }
    }

    @Entity
    @Table(name = "SUB_ENTITY")
    public static class SubEntity {

        public Integer subId;
        public MyEntity myEntity;

        @Id
        @SequenceGenerator(name = "SUB_ENTITY_SEQ", sequenceName = "SUB_ENTITY_SEQ", allocationSize = 100)
        @GeneratedValue(generator = "SUB_ENTITY_SEQ")
        @Column(name = "SUB_ID", nullable = false, unique = true)
        public Integer getSubId() {
            return subId;
        }

        public void setSubId(Integer subId) {
            this.subId = subId;
        }

        @JoinColumn(name = "ID", referencedColumnName = "ID", nullable = false)
        @ManyToOne(fetch = FetchType.EAGER, targetEntity = MyEntity.class)
        public MyEntity getMyEntity() {
            return myEntity;
        }

        public void setMyEntity(MyEntity myEntity) {
            this.myEntity = myEntity;
        }

    }
}
