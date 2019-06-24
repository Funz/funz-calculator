/*
 * Created on 25 juin 07 by richet
 */
package org.funz.calculator;

import org.w3c.dom.Element;

public class Code {
	public String name, command, pluginURL;

	Code(Element e) {
		name = e.getAttribute(Calculator.ATTR_NAME);
		pluginURL = e.getAttribute(Calculator.ATTR_PLUGIN);
		command = e.getAttribute(Calculator.ATTR_COMMAND);
	}
}