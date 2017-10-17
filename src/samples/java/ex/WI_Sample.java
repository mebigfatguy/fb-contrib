package ex;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

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

    public void foo() {
        MyBean b = new MyBean();
    }
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

@Component
class MyBean {
}
