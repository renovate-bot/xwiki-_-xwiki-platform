/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.realtime.test.po;

import org.openqa.selenium.By;
import org.xwiki.test.ui.po.BaseModal;

/**
 * Modal displayed when clicking on "Summarize and Done" button in realtime edition.
 *
 * @version $Id$
 */
public class SummaryModal extends BaseModal
{
    /**
     * Default constructor.
     */
    public SummaryModal()
    {
        super(By.id("realtime-changeSummaryModal"));
    }

    /**
     * Fill the summary textarea with the given content.
     * @param summary the text to put in the summary textarea
     */
    public void setSummary(String summary)
    {
        getDriver().findElementWithoutWaiting(this.container, By.id("realtime-changeSummaryModal-summary"))
            .sendKeys(summary);
    }

    /**
     * Check or uncheck the minor change checkbox.
     */
    public void toggleMinorEdit()
    {
        getDriver().findElementWithoutWaiting(this.container,
            By.cssSelector("input[type='checkbox'][name='minorChange']")).click();
    }

    /**
     * Click on the save button and eventually wait for the saved success message.
     * @param waitSuccess if {@code true} wait for the "Saved" success message
     */
    public void clickSave(boolean waitSuccess)
    {
        getDriver().findElementWithoutWaiting(this.container, By.cssSelector(".btn-primary")).click();
        if (waitSuccess) {
            waitForNotificationSuccessMessage("Saved");
        }
    }
}
