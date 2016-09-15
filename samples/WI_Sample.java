import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class WI_Sample extends Parent {
    @Autowired
    SingletonBean mySBean;

    @Autowired
    @Qualifier("special")
    SingletonBean myOtherSBean;

    @Autowired
    GenerifiedBean<String> fpStringBean;

    @Autowired
    GenerifiedBean<Integer> fpIntBean;
}

class Parent {
    @Autowired
    SingletonBean sbean;

    @Autowired
    @Qualifier("special")
    SingletonBean pbean;
}

interface SingletonBean {

}

interface GenerifiedBean<T> {
}
