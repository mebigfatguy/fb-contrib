import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

public class WI_Sample {
    @Autowired
    SingletonBean mySBean;

    @Autowired
    SingletonBean myOtherSBean;

    @Autowired
    PrototypeBean myPBean;

    @Autowired
    PrototypeBean myOtherPBean;
}

class Parent {
    @Autowired
    SingletonBean sbean;

    @Autowired
    SingletonBean pbean;
}

interface SingletonBean {

}

@Component
class SingletonBeanImpl implements SingletonBean {

}

interface PrototypeBean {

}

@Component
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS, value = "prototype")
class PrototypeBeanImpl implements PrototypeBean {

}
