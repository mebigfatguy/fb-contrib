import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.springframework.transaction.annotation.Transactional;

public class JPAI_Sample {

    @Transactional
    private void writeData() {
        
    }
    
    @Entity
    @Table(name = "MY_ENTITY")
    public static class MyEntity {
        
        public Integer id;
        
        @Id
        @SequenceGenerator(name = "MY_ENTITY_SEQ", sequenceName = "MY_ENTITY_SEQ", allocationSize = 100)
        @GeneratedValue(generator = "MY_ENTITY_SEQ")
        public Integer getId() {
            return id;
        }
        
        public void setId(Integer id) {
            this.id = id;
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
}
