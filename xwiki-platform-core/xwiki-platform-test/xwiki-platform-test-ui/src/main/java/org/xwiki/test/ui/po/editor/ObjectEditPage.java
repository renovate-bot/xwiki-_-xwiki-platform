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
package org.xwiki.test.ui.po.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * Represents the common actions possible on all Pages when using the "edit" action with the "object" editor.
 *
 * @version $Id$
 * @since 3.2M3
 */
public class ObjectEditPage extends EditPage
{
    @FindBy(id = "classname")
    private WebElement classNameField;

    @FindBy(name = "action_objectadd")
    private WebElement classNameSubmit;

    /**
     * Go to the passed page in object edit mode.
     */
    public static ObjectEditPage gotoPage(String space, String page)
    {
        getUtil().gotoPage(space, page, "edit", "editor=object");
        return new ObjectEditPage();
    }

    /**
     * Edit the specified page in object edit mode.
     *
     * @param pageReference the reference of the page to edit
     * @return the object edit page
     * @since 16.10.6
     * @since 17.3.0RC1
     */
    public static ObjectEditPage gotoPage(EntityReference pageReference)
    {
        getUtil().gotoPage(pageReference, "edit", "editor=object");
        return new ObjectEditPage();
    }

    public ObjectEditPane addObject(String className)
    {
        // Ensure to scroll to the action button, in case we where below in the page.
        getDriver().scrollTo(classNameSubmit);
        new SuggestInputElement(this.classNameField).click().waitForSuggestions().selectByValue(className);

        final By objectsLocator = By.cssSelector("[id='xclass_" + className + "'] .xobject");
        final int initialObjectCount = getDriver().findElementsWithoutWaiting(objectsLocator).size();
        this.classNameSubmit.click();

        // Make sure we wait for the element to appear since there's no page refresh.
        getDriver().waitUntilCondition(new ExpectedCondition<Boolean>()
        {
            @Override
            public Boolean apply(WebDriver driver)
            {
                return Boolean.valueOf(driver.findElements(objectsLocator).size() > initialObjectCount);
            }
        });

        List<ObjectEditPane> objects = getObjectsOfClass(className);
        return objects.get(objects.size() - 1);
    }

    public ObjectEditPane addObjectFromInlineLink(String className)
    {
        final By objectsLocator = By.cssSelector("[id='xclass_" + className + "'] .xobject");
        final int initialObjectCount = getDriver().findElements(objectsLocator).size();

        getDriver().findElement(By.cssSelector("[id='add_xobject_" + className + "'] .xobject-add-control")).click();

        // Make sure we wait for the element to appear since there's no page refresh.
        getDriver().waitUntilCondition(new ExpectedCondition<Boolean>()
        {
            @Override
            public Boolean apply(WebDriver driver)
            {
                return Boolean.valueOf(driver.findElements(objectsLocator).size() > initialObjectCount);
            }
        });

        List<ObjectEditPane> objects = getObjectsOfClass(className);
        return objects.get(objects.size() - 1);
    }

    /**
     * @since 4.3M2
     */
    public void removeAllObjects(String className)
    {
        List<WebElement> objectContainers = getDriver().findElementsWithoutWaiting(
            By.xpath("//div[starts-with(@id, '" + "xobject_" + className + "_')]"));
        // Exclude ids ending with _title and _content since we have 3 matches for each id...
        List<WebElement> validElements = new ArrayList<WebElement>();
        for (WebElement element : objectContainers) {
            String id = element.getAttribute("id");
            if (!id.endsWith("_content") && !id.endsWith("_title")) {
            validElements.add(element);
            }
        }
        for (WebElement element : validElements) {
            deleteObject(By.id(element.getAttribute("id")));
        }
    }

    public void deleteObject(String className, int index)
    {
        deleteObject(By.id("xobject_" + className + "_" + index));
    }

    /**
     * @since 4.3M2
     */
    public void deleteObject(By objectLocator)
    {
        final WebElement objectContainer = getDriver().findElement(objectLocator);
        WebElement deleteLink = objectContainer.findElement(By.className("delete"));
        deleteLink.click();

        // Expect a confirmation box
        getDriver().waitUntilElementIsVisible(By.className("xdialog-box-confirmation"));
        getDriver().findElement(By.cssSelector(".xdialog-box-confirmation input[value='Yes']")).click();
        getDriver().waitUntilElementDisappears(objectLocator);
    }

    public void removeAllDeprecatedProperties()
    {
        getDriver().findElement(By.className("syncAllProperties")).click();
        getDriver().waitUntilElementDisappears(By.className("deprecatedProperties"));
    }

    /**
     * @param className a class name
     * @param propertyName a class field name
     * @return {@code true} if the specified class field is listed as deprecated, {@code false} otherwise
     */
    public boolean isPropertyDeprecated(String className, String propertyName)
    {
        WebElement xclass = getDriver().findElement(By.id("xclass_" + className));
        List<WebElement> deprecatedPropertiesElements = xclass.findElements(By.className("deprecatedProperties"));
        if (deprecatedPropertiesElements.size() > 0) {
            String xpath = "//label[. = '" + propertyName + ":']";
            return deprecatedPropertiesElements.get(0).findElements(By.xpath(xpath)).size() > 0;
        }
        return false;
    }

    public String getURL(String space, String page)
    {
        return getUtil().getURL(space, page, "edit", "editor=object");
    }

    /** className will look something like "XWiki.XWikiRights" */
    public List<ObjectEditPane> getObjectsOfClass(String className)
    {
        return getObjectsOfClass(className, true);
    }

    /**
     * Retrieve all objects of the given class name, and expand them if needed.
     * Note that if {@code displayAllObjects} is set to {@code false} then you need to be careful to call
     * {@link ObjectEditPane#displayObject()} to load the xobjects information if you need to access them.
     *
     * @param className the name of the class for which to retrieve the object (e.g. XWiki.XWikiRights)
     * @param displayAllObjects if {@code true} expand the objects before returning them.
     * @return the list of {@link ObjectEditPane} corresponding to all objects of the given class.
     * @since 13.1RC1
     */
    public List<ObjectEditPane> getObjectsOfClass(String className, boolean displayAllObjects)
    {
        WebElement classElement;
        try {
            classElement = getDriver().findElementWithoutWaiting(By.id("xclass_" + className));
        } catch (NoSuchElementException e) {
            // if we cannot find the class elements it means there's no object of this class.
            return Collections.emptyList();
        }
        // Don't wait for objects, when the class is there, the objects should be there, too.
        List<WebElement> elements =
            getDriver().findElementsWithoutWaiting(classElement, By.className("xobject-content"));
        List<ObjectEditPane> objects = new ArrayList<ObjectEditPane>(elements.size());
        for (WebElement element : elements) {
            int objectNumber = Integer.parseInt(element.getAttribute("id").split("_")[2]);
            ObjectEditPane editPane = new ObjectEditPane(By.id(element.getAttribute("id")), className, objectNumber);
            if (displayAllObjects) {
                editPane.displayObject();
            }
            objects.add(editPane);
        }
        return objects;
    }

    public boolean hasObject(String className) {
        return !getDriver().findElementsWithoutWaiting(By.id("xclass_" + className)).isEmpty();
    }
}
