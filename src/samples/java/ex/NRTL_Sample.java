package ex;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

@SuppressWarnings("all")
public class NRTL_Sample extends TagSupport {
    private String sample;
    private String sample2;

    public void setSample(String s) {
        sample = s;
    }

    @Override
    public int doStartTag() throws JspException {
        try {
            sample += Math.random();
            sample2 += sample;
            pageContext.getOut().print(sample2);
        } catch (Exception ex) {
            throw new JspTagException("NRTL_Sample: " + ex.getMessage());
        }
        return SKIP_BODY;
    }

    public void setSample2(String s) {
        sample2 = s;
    }

    @Override
    public int doEndTag() {
        return EVAL_PAGE;
    }

}
