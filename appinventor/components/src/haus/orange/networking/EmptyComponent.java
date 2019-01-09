package haus.orange.networking;

import com.google.appinventor.components.runtime.*;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;

/*
Jacob Bashista 1/5/19

EmptyComponent is a component designed to contain no content
and act as a base for other components to be built off.
*/


@DesignerComponent(version = 1,
        description = "Empty Extension Component",
        category = ComponentCategory.EXTENSION,
        nonVisible = true,
        iconName = "images/extension.png")
@SimpleObject(external = true)
public class EmptyComponent extends AndroidNonvisibleComponent
        implements Component {

    public EmptyComponent(ComponentContainer container){
        super(container.$form());
    }
}
