package org.funz.calculator.plugin;

/** Used to send a data single line string to the user's console.
* Data sent will be displayed in front of the case under treatment.
* @param inof single line string
* @return true if data has been sent successfully
*/
public interface DataChannel {
	public boolean sendInfomationLineToConsole(String info);

	// public boolean sendFileToConsole( File file );
}
