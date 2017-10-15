package beanconfig.polymorphic;

import beanconfig.polymorphic.VisibleTypeId.PropertyBean;

public class VisibleTypeIdConfigs {

    public static class VisibleTypeConfig {

        private PropertyBean bean;

        public PropertyBean getBean() {
            return bean;
        }

        public void setBean(PropertyBean bean) {
            this.bean = bean;
        }
    }

}
