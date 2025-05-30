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
package org.xwiki.administration.test.po;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.xwiki.test.ui.po.AbstractRegistrationPage;

/**
 * Represents a registration page in a modal.
 *
 * @version $Id$
 * @since 10.9
 */
public class RegistrationModal extends AbstractRegistrationPage
{
    @FindBy(css = "#createUserModal .btn-primary")
    private WebElement createButton;

    public static RegistrationModal gotoPage()
    {
        UsersAdministrationSectionPage sectionPage = UsersAdministrationSectionPage.gotoPage();
        sectionPage.getUsersLiveData().getTableLayout().waitUntilReady();
        return sectionPage.clickAddNewUser();
    }

    @Override
    public void clickRegister()
    {
        this.createButton.click();
    }

    /**
     * Wait for the registration form to show no error message.
     */
    public void waitForLiveValidationSuccess()
    {
        getDriver()
            .waitUntilCondition(input -> input.findElements(By.cssSelector("#createUserModal .LV_invalid")).isEmpty());
    }

    @Override
    public void waitUntilPageIsReady()
    {
        getDriver().waitUntilElementIsVisible(By.cssSelector("#createUserModal .modal-body form#register"));
    }

    @Override
    public boolean validationFailureMessagesInclude(String message)
    {
        // Check both the live validation errors and the server side errors.
        By serverSideErrorMessage =
            By.xpath(String.format("//div[contains(@class,'xnotification-error') and contains(., '%s')]", message));
        return super.validationFailureMessagesInclude(message)
            || getDriver().findElementsWithoutWaiting(serverSideErrorMessage).size() > 0;
    }
}
