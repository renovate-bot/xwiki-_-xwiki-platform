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
package org.xwiki.ckeditor.test.ui;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.xwiki.ckeditor.test.po.AutocompleteDropdown;
import org.xwiki.ckeditor.test.po.CKEditor;
import org.xwiki.ckeditor.test.po.LinkDialog;
import org.xwiki.ckeditor.test.po.RichTextAreaElement;
import org.xwiki.ckeditor.test.po.image.ImageDialogEditModal;
import org.xwiki.ckeditor.test.po.image.ImageDialogSelectModal;
import org.xwiki.ckeditor.test.po.image.edit.ImageDialogAdvancedEditForm;
import org.xwiki.ckeditor.test.po.image.edit.ImageDialogStandardEditForm;
import org.xwiki.ckeditor.test.po.image.select.ImageDialogIconSelectForm;
import org.xwiki.ckeditor.test.po.image.select.ImageDialogUrlSelectForm;
import org.xwiki.flamingo.skin.test.po.AttachmentsPane;
import org.xwiki.flamingo.skin.test.po.AttachmentsViewPage;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.docker.junit5.TestLocalReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.docker.junit5.WikisSource;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.browser.IgnoreBrowser;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;
import org.xwiki.test.ui.po.editor.WikiEditPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test of the CKEditor Image Plugin.
 *
 * @version $Id$
 * @since 14.7RC1
 */
@UITest(
    properties = {
        "xwikiDbHbmCommonExtraMappings=notification-filter-preferences.hbm.xml"
    },
    extraJARs = {
        // It's currently not possible to install a JAR contributing a Hibernate mapping file as an Extension. Thus
        // we need to provide the JAR inside WEB-INF/lib. See https://jira.xwiki.org/browse/XWIKI-8271
        "org.xwiki.platform:xwiki-platform-notifications-filters-default",

        // The macro service uses the extension index script service to get the list of uninstalled macros (from
        // extensions) which expects an implementation of the extension index. The extension index script service is a
        // core extension so we need to make the extension index also core.
        "org.xwiki.platform:xwiki-platform-extension-index",
        // Solr search is used to get suggestions for the link quick action.
        "org.xwiki.platform:xwiki-platform-search-solr-query"
    },
    resolveExtraJARs = true
)
class ImageIT extends AbstractCKEditorIT
{
    @BeforeAll
    void beforeAll(TestUtils setup, TestConfiguration testConfiguration) throws Exception {
        setup.loginAsSuperAdmin();
        waitForSolrIndexing(setup, testConfiguration);
    }
    
    @BeforeEach
    void setUp(TestUtils setup, TestReference testReference)
    {
        createAndLoginStandardUser(setup);
        setup.loginAsSuperAdmin();
        setup.deletePage(testReference, true);
        DocumentReference configurationReference = getConfigurationReference(setup);
        setup.deletePage(configurationReference);
        DocumentReference imageStylesReference =
            new DocumentReference(setup.getCurrentWiki(), List.of("Image", "Style", "Code"), "ImageStyles");
        setup.deletePage(imageStylesReference, true);
        // Calling "loginAsSuperAdmin" right after "loginStandardUser" can lead to the loginAsSuperAdmin call
        // being ignored. It is also bad for test performance as the first login is done for nothing. Please remember to
        // log in with the standard user as soon as superadmin is not needed in your tests. 
    }

    @AfterEach
    void afterEach(TestUtils setup)
    {
        setup.maybeLeaveEditMode();
    }

    @Test
    @Order(1)
    void insertImage(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a first image.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.clickInsert();
        // Move the focus out of the newly inserted image widget.
        editor.getRichTextArea().sendKeys(Keys.RIGHT);
        // Insert a second image, with a caption.
        imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.switchToStandardTab().clickCaptionCheckbox();
        imageDialogEditModal.clickInsert();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("""
            [[image:image.gif]]

            [[Caption>>image:image.gif]]

            \040""", savedPage.editWiki().getContent());
    }

    @Test
    @Order(2)
    void insertImageWithStyle(TestUtils setup, TestReference testReference) throws Exception
    {
        createBorderedStyle(setup);

        // Then test the image styles on the image dialog as a standard user.
        loginStandardUser(setup);
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a first image.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        ImageDialogStandardEditForm imageDialogStandardEditForm = imageDialogEditModal.switchToStandardTab();
        // Assert the available image styles as well as the one currently selected.
        assertEquals(Set.of("", "bordered"), imageDialogStandardEditForm.getListImageStyles());
        assertEquals("", imageDialogStandardEditForm.getCurrentImageStyle());
        imageDialogStandardEditForm.setImageStyle("Bordered");
        imageDialogEditModal.clickInsert();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[image:image.gif||data-xwiki-image-style=\"bordered\" "
                + "data-xwiki-image-style-border=\"true\"]]",
            savedPage.editWiki().getContent());

        // Re-edit the page.
        savedPage.editWYSIWYG();
        editor = new CKEditor("content").waitToLoad();

        // Focus on the image to edit.
        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.id("Iimage.gif")).click());

        imageDialogEditModal = editor.getToolBar().editImage();
        imageDialogStandardEditForm = imageDialogEditModal.switchToStandardTab();
        assertEquals(Set.of("", "bordered"), imageDialogStandardEditForm.getListImageStyles());
        assertEquals("bordered", imageDialogStandardEditForm.getCurrentImageStyle());

        // Re-insert and save the page to avoid triggering a javascript alert for unsaved page.
        imageDialogEditModal.clickInsert();
        wysiwygEditPage.clickSaveAndView();
    }

    @Test
    @Order(3)
    void insertIcon(TestUtils setup, TestReference testReference)
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        ViewPage newPage = setup.gotoPage(testReference);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a first image.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        ImageDialogIconSelectForm imageDialogIconSelectForm = imageDialogSelectModal.switchToIconTab();
        imageDialogIconSelectForm.setIconValue("accept");
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.clickInsert();
        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[image:icon:accept]]", savedPage.editWiki().getContent());
    }

    @Test
    @Order(4)
    void insertUrl(TestUtils setup, TestReference testReference)
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        ViewPage newPage = setup.gotoPage(testReference);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a first image.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        ImageDialogUrlSelectForm imageDialogUrlSelectForm = imageDialogSelectModal.switchToUrlTab();
        imageDialogUrlSelectForm.setUrlValue("http://mysite.com/myimage.png");
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.clickInsert();
        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[image:http://mysite.com/myimage.png]]", savedPage.editWiki().getContent());
    }

    /**
     * Verify that the {@code wikigeneratedid} class is correctly preserved when the caption is activated on an
     * existing image and thus the id is not persisted, see
     * <a href="https://jira.xwiki.org/browse/XWIKI-20652">XWIKI-20652</a>.
     * Also verify that custom ids are preserved, nevertheless.
     */
    @Test
    @Order(5)
    void activateCaptionIdPersistence(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Insert a first image.
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a first image.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.clickInsert();

        editor.getRichTextArea().sendKeys(Keys.RIGHT, Keys.ENTER, "Some text", Keys.ENTER);

        imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.clickInsert();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        WikiEditPage wikiEditPage = savedPage.editWiki();
        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[image:image.gif]]\n\nSome text\n\n[[image:image.gif]]", wikiEditPage.getContent());
        wikiEditPage.setContent("[[image:image.gif]]\n\nSome text\n\n[[image:image.gif||id=\"customID\"]]");
        newPage = wikiEditPage.clickSaveAndView();
        wysiwygEditPage = newPage.editWYSIWYG();
        editor = new CKEditor("content").waitToLoad();
        for (String id : List.of("Iimage.gif", "customID")) {
            editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.id(id)).click());
            imageDialogEditModal = editor.getToolBar().editImage();
            imageDialogEditModal.switchToStandardTab().clickCaptionCheckbox();
            imageDialogEditModal.clickInsert();
        }
        savedPage = wysiwygEditPage.clickSaveAndView();

        assertEquals("[[Caption>>image:image.gif]]\n\nSome text\n\n[[Caption>>image:image.gif||id=\"customID\"]]",
            savedPage.editWiki().getContent());
    }

    @Test
    @Order(6)
    void imageWithCaption(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Upload an attachment to test with.
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a with caption and alignment to center.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.switchToStandardTab().clickCaptionCheckbox();
        imageDialogEditModal.switchToAdvancedTab().selectCenterAlignment();
        imageDialogEditModal.clickInsert();
        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[Caption>>image:image.gif||data-xwiki-image-style-alignment=\"center\"]]\n\n ",
            savedPage.editWiki().getContent());

        // Re-edit the page.
        savedPage.editWYSIWYG();
        editor = new CKEditor("content").waitToLoad();

        // Focus on the image to edit.
        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        imageDialogEditModal = editor.getToolBar().editImage();
        imageDialogEditModal.switchToStandardTab().clickCaptionCheckbox();
        imageDialogEditModal.clickInsert();
        savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[image:image.gif||data-xwiki-image-style-alignment=\"center\"]]\n\n ",
            savedPage.editWiki().getContent());

        // Edit again to set the caption a second time.
        savedPage.editWYSIWYG();
        editor = new CKEditor("content").waitToLoad();

        // Focus on the image to edit.
        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        imageDialogEditModal = editor.getToolBar().editImage();
        imageDialogEditModal.switchToStandardTab().clickCaptionCheckbox();
        imageDialogEditModal.clickInsert();
        savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[Caption>>image:image.gif||data-xwiki-image-style-alignment=\"center\"]]\n\n ",
            savedPage.editWiki().getContent());
    }

    @Test
    @Order(7)
    void imageWrappedInLink(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Upload an attachment to test with.
        String attachmentName = "image.gif";
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        WikiEditPage wikiEditPage = newPage.editWiki();
        wikiEditPage.setContent("""
            [[[[image:image.gif]]>>doc:]]

            (% a='b' %)[[[[image:image.gif]]>>doc:]]

            (% a="b" %)
            [[aaaa>>image:image.gif]]
            """);
        ViewPage savedPage = wikiEditPage.clickSaveAndView();

        assertEquals("""
            [[[[image:image.gif]]>>doc:]]

            (% a='b' %)[[[[image:image.gif]]>>doc:]]

            (% a="b" %)
            [[aaaa>>image:image.gif]]""", savedPage.editWiki().getContent());

        // Re-edit the page.
        WYSIWYGEditPage wysiwygEditPage = savedPage.editWYSIWYG();
        new CKEditor("content").waitToLoad();
        savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content is not altered when edited with CKEditor (expect for the additional escaping)
        assertEquals("""
            [[~[~[image:image.gif~]~]>>doc:]]

            (% a="b" %)[[~[~[image:image.gif~]~]>>doc:]]

            (% a="b" %)
            [[aaaa>>image:image.gif]]""", savedPage.editWiki().getContent());
    }

    @Test
    @Order(8)
    void imageWrappedInLinkUI(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Upload an attachment to test with.
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a with caption and alignment to center.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        imageDialogSelectModal.clickSelect().clickInsert();

        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        editor.getToolBar().insertOrEditLink().setResourceValue("doc:", false).submit();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        assertEquals("[[~[~[image:image.gif~]~]>>doc:]]", savedPage.editWiki().getContent());
    }

    @Test
    @Order(9)
    void imageWithLinkAndCaptionUI(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Upload an attachment to test with.
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a with caption and alignment to center.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.switchToStandardTab().clickCaptionCheckbox();
        imageDialogEditModal.switchToAdvancedTab().selectCenterAlignment();
        imageDialogEditModal.clickInsert();

        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        editor.getToolBar().insertOrEditLink().setResourceValue("doc:Main.WebHome", false).submit();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        assertEquals("""
            [[~[~[Caption~>~>image:image.gif~|~|data-xwiki-image-style-alignment="center"~]~]>>doc:Main.WebHome]]

            \040""", savedPage.editWiki().getContent());
        // Test that when re-editing the image, the link, caption and alignment are still set.
        wysiwygEditPage = savedPage.editWYSIWYG();
        editor = new CKEditor("content").waitToLoad();

        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        imageDialogEditModal = editor.getToolBar().editImage();
        // Verify that the caption and alignment are still set.
        ImageDialogStandardEditForm standardEditForm = imageDialogEditModal.switchToStandardTab();
        assertTrue(standardEditForm.isCaptionCheckboxChecked());
        ImageDialogAdvancedEditForm advancedEditForm = imageDialogEditModal.switchToAdvancedTab();
        assertEquals("center", advancedEditForm.getAlignment());
        imageDialogEditModal.close();

        // Verify that the link is still set.
        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());
        LinkDialog linkSelectorModal = editor.getToolBar().insertOrEditLink();
        assertEquals("doc", linkSelectorModal.getSelectedResourceType());
        assertEquals("Main.WebHome", linkSelectorModal.getSelectedResourceReference());
        linkSelectorModal.cancel();

        // Change the caption to ensure that saving again works.
        editor.executeOnEditedContent(
            () -> setup.getDriver().findElement(By.cssSelector("figcaption"))
                // Go to the start of the caption and insert "New ".
                .sendKeys(Keys.HOME, "New "));
        savedPage = wysiwygEditPage.clickSaveAndView();

        assertEquals("""
            [[~[~[New Caption~>~>image:image.gif~|~|data-xwiki-image-style-alignment="center"~]~]>>doc:Main.WebHome]]

            \040""", savedPage.editWiki().getContent());
    }

    @Test
    @Order(10)
    void editLegacyCenteredImage(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Upload an attachment to test with.
        String attachmentName = "image.gif";
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        WikiEditPage wikiEditPage = newPage.editWiki();
        wikiEditPage.setContent("(% style='text-align: center' %)\n"
            + "[[image:image.gif]]");
        ViewPage viewPage = wikiEditPage.clickSaveAndView();

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = viewPage.editWYSIWYG();
        new CKEditor("content").waitToLoad();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        assertEquals("[[image:image.gif||data-xwiki-image-style-alignment=\"center\"]]",
            savedPage.editWiki().getContent());
    }

    @Test
    @Order(11)
    void updateImageSize(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Upload an attachment to test with.
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a with caption and alignment to center.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.switchToAdvancedTab().setWidth(100);
        imageDialogEditModal.clickInsert();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        assertEquals("[[image:image.gif||height=\"100\" width=\"100\"]]", savedPage.editWiki().getContent());

        wysiwygEditPage = savedPage.editWYSIWYG();
        editor = new CKEditor("content").waitToLoad();

        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        imageDialogEditModal = editor.getToolBar().editImage();
        imageDialogEditModal.switchToAdvancedTab().setWidth(50);
        imageDialogEditModal.clickInsert();

        wysiwygEditPage.clickSaveAndView();

        assertEquals("[[image:image.gif||height=\"50\" width=\"50\"]]", savedPage.editWiki().getContent());
    }

    @Test
    @Order(12)
    void updateExternalImageSize(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Upload an attachment to test with.
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        String imageURL = setup.getURL(attachmentReference, "download", "");
        uploadAttachment(setup, testReference, attachmentName);

        WYSIWYGEditPage wysiwygEditPage = setup.gotoPage(testReference).editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        // Insert a with caption and alignment to center.
        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();

        
        imageDialogSelectModal.switchToUrlTab().setUrlValue(imageURL);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        imageDialogEditModal.switchToAdvancedTab().setWidth(100);
        imageDialogEditModal.clickInsert();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        assertEquals("[[image:" + imageURL + "||height=\"100\" width=\"100\"]]", savedPage.editWiki().getContent());

        wysiwygEditPage = savedPage.editWYSIWYG();
        editor = new CKEditor("content").waitToLoad();

        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        imageDialogEditModal = editor.getToolBar().editImage();
        imageDialogEditModal.switchToAdvancedTab().setWidth(50);
        imageDialogEditModal.clickInsert();

        wysiwygEditPage.clickSaveAndView();

        assertEquals("[[image:" + imageURL + "||height=\"50\" width=\"50\"]]", savedPage.editWiki().getContent());
    }
    
    @Test
    @Order(13)
    void quickInsertImageThisPage(TestUtils setup,
            TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Upload an attachment to the page.
        String attachmentName = "image.gif";
        uploadAttachment(setup, testReference, attachmentName);
        
        // Move to the WYSIWYG edition page.
        edit(setup, testReference, false);
        
        // Run the image quick action.
        textArea.sendKeys("/image");
        AutocompleteDropdown qa = new AutocompleteDropdown().waitForItemSelected("/image", "Image");
        textArea.sendKeys(Keys.ENTER);
        qa.waitForItemSubmitted();
        
        // Select the newly uploaded image.
        textArea.sendKeys("image");
        AutocompleteDropdown img = new AutocompleteDropdown().waitForItemSelected("img::image", attachmentName);
        assertIterableEquals(List.of("This page"), img.getSelectedItem().getBadges());
        textArea.sendKeys(Keys.ENTER);
        img.waitForItemSubmitted();

        assertSourceEquals("[[image:image.gif]]");
    }

    @Test
    @Order(14)
    void quickInsertImageOtherPage(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);
        // Create a child page.
        DocumentReference otherPage = new DocumentReference("attachmentOtherPage",
            testReference.getLastSpaceReference());
        
        // Attach an image named "otherImage.gif" to the other page.
        String otherAttachmentName = "otherImage.gif";
        uploadAttachment(setup, otherPage, otherAttachmentName);
        
        String attachmentName = "image.gif";
        uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        edit(setup, testReference, false);
        
        // Run the image quick action.
        textArea.sendKeys("/image");
        AutocompleteDropdown qa = new AutocompleteDropdown().waitForItemSelected("/image", "Image");
        textArea.sendKeys(Keys.ENTER);
        qa.waitForItemSubmitted();
        
        // With the query "image", the image from the current page should be first.
        textArea.sendKeys("image");
        AutocompleteDropdown img = new AutocompleteDropdown().waitForItemSelected("img::image", attachmentName);
        assertIterableEquals(List.of("This page"), img.getSelectedItem().getBadges());
        // The image from the other page should be right after, because last uploaded on the instance.
        textArea.sendKeys(Keys.DOWN);
        img.waitForItemSelected("img::image", otherAttachmentName);
        assertIterableEquals(List.of(""), img.getSelectedItem().getBadges());
        textArea.sendKeys(Keys.ENTER);
        img.waitForItemSubmitted();
        textArea.waitUntilContentContains(otherAttachmentName);

        assertSourceEquals("[[image:attachmentOtherPage@otherImage.gif]]");
    }
    
    @ParameterizedTest
    @WikisSource(mainWiki = false)
    @Order(15)
    void quickInsertImageSubWiki(WikiReference wiki, TestUtils setup,
            TestLocalReference testLocalReference, TestReference testReference) throws Exception
    {
        // Cleanup.
        DocumentReference subwikiDocumentReference = new DocumentReference(testLocalReference, wiki);
        setup.deletePage(subwikiDocumentReference, true);

        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);

        // Upload image to subwiki.
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, subwikiDocumentReference);
        uploadAttachment(setup, subwikiDocumentReference, attachmentName);
        
        // Move to the WYSIWYG edition page on the main wiki.
        // We update the currentWiki attribute manually because TestUtils.gotoPage updates it only after
        // navigating to the target page.
        setup.setCurrentWiki("xwiki");
        edit(setup, testReference, false);
        
        // Run the image quick action.
        textArea.sendKeys("/image");
        AutocompleteDropdown qa = new AutocompleteDropdown().waitForItemSelected("/image", "Image");
        textArea.sendKeys(Keys.ENTER);
        qa.waitForItemSubmitted();
        
        // Since there is no image on the current page, the image from the subwiki should be first.
        textArea.sendKeys("image");
        AutocompleteDropdown img = new AutocompleteDropdown().waitForItemSelected("img::image", attachmentName);
        assertIterableEquals(List.of("External"), img.getSelectedItem().getBadges());
        textArea.sendKeys(Keys.ENTER);
        img.waitForItemSubmitted();
        
        assertSourceEquals("[[image:" + setup.serializeReference(attachmentReference) + "]]");
    }

    @Test
    @Order(16)
    void forceDefaultStyle(TestUtils setup, TestReference testReference) throws Exception
    {
        // Change the configuration to have a default style and force it.
        DocumentReference configurationReference = getConfigurationReference(setup);
        setup.updateObject(configurationReference, "Image.Style.Code.ConfigurationClass", 0,
            "defaultStyle", "borderedPage",
            "forceDefaultStyle", "1");

        // Create the image style as an admin.
        createBorderedStyle(setup);

        // Then test the image styles on the image dialog as a standard user.
        loginStandardUser(setup);
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        ImageDialogStandardEditForm imageDialogStandardEditForm = imageDialogEditModal.switchToStandardTab();
        // Assert the available image styles as well as the one currently selected.
        assertEquals(Set.of(""), imageDialogStandardEditForm.getListImageStyles());
        assertEquals("", imageDialogStandardEditForm.getCurrentImageStyle());
        assertEquals("Bordered", imageDialogStandardEditForm.getCurrentImageStyleLabel());
        ImageDialogAdvancedEditForm imageDialogAdvancedEditForm = imageDialogEditModal.switchToAdvancedTab();
        assertFalse(imageDialogAdvancedEditForm.isWidthEnabled());
        imageDialogEditModal.clickInsert();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[image:image.gif||data-xwiki-image-style-border=\"true\"]]", savedPage.editWiki().getContent());
    }

    @Test
    @Order(17)
    void updateImageStyleSeveralTimes(TestUtils setup, TestReference testReference) throws Exception
    {
        createBorderedStyle(setup);

        // Then test the image styles on the image dialog as a standard user.
        loginStandardUser(setup);
        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = newPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        ImageDialogSelectModal imageDialogSelectModal = editor.getToolBar().insertImage();
        imageDialogSelectModal.switchToTreeTab().selectAttachment(attachmentReference);
        ImageDialogEditModal imageDialogEditModal = imageDialogSelectModal.clickSelect();
        ImageDialogStandardEditForm imageDialogStandardEditForm = imageDialogEditModal.switchToStandardTab();
        imageDialogStandardEditForm.setImageStyle("Bordered");
        imageDialogEditModal.clickInsert();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[image:image.gif||data-xwiki-image-style=\"bordered\" "
            + "data-xwiki-image-style-border=\"true\"]]", savedPage.editWiki().getContent());

        wysiwygEditPage = savedPage.editWYSIWYG();
        editor = new CKEditor("content").waitToLoad();

        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        imageDialogEditModal = editor.getToolBar().editImage();

        imageDialogEditModal.switchToStandardTab().setImageStyle("---");
        imageDialogEditModal.clickInsert();

        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        imageDialogEditModal = editor.getToolBar().editImage();
        imageDialogEditModal.switchToStandardTab().setImageStyle("Bordered");
        imageDialogEditModal.clickInsert();

        savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("[[image:image.gif||data-xwiki-image-style=\"bordered\" "
            + "data-xwiki-image-style-border=\"true\"]]", savedPage.editWiki().getContent());
    }

    @Test
    @Order(18)
    void pasteAndEditExternalImage(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.loginAsSuperAdmin();

        String attachmentName = "image.gif";
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, testReference);
        String imageURL = setup.getURL(attachmentReference, "download", "");

        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        // After this method, the clipboard contains an html content with some text and an image.
        copyHTML(setup, newPage, """
                <p>
                  a <img src="%s" alt="Test alt"> b
                </p>
            """.formatted(imageURL));

        DocumentReference subPageReference = new DocumentReference("Paste", testReference.getLastSpaceReference());
        WYSIWYGEditPage wysiwygEditPage = setup.gotoPage(subPageReference).editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        RichTextAreaElement richTextArea = editor.getRichTextArea();
        richTextArea.clear();
        richTextArea.sendKeys(Keys.chord(Keys.CONTROL, "v"));
        richTextArea.verifyContent(content -> {
            content.getImages().get(0).click();
        });

        // Verify that it's possible to edit a freshly pasted image.
        ImageDialogEditModal imageDialogEditModal = editor.getToolBar().editImage();
        imageDialogEditModal.switchToAdvancedTab().setWidth(100);
        imageDialogEditModal.clickInsert();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("a [[image:" + imageURL + "||alt=\"Test alt\" height=\"100\" width=\"100\"]] b", savedPage.editWiki().getContent());
    }

    @Test
    @Order(19)
    void editListWithImage(TestUtils setup, TestReference testReference) throws Exception
    {
        // Then test the image styles on the image dialog as a standard user.
        loginStandardUser(setup);
        String attachmentName = "image.gif";
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        WikiEditPage wikiEditPage = newPage.editWiki();
        wikiEditPage.setContent("""
            * Item 1
            * Item 2 [[image:image.gif]]""");
        wikiEditPage.clickSaveAndView();

        WYSIWYGEditPage wysiwygEditPage = wikiEditPage.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();

        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());

        editor.getToolBar().clickNumberedList();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        // Verify that the content matches what we did using CKEditor.
        assertEquals("""
            * Item 1

            1. Item 2 [[image:image.gif]]""", savedPage.editWiki().getContent());
    }

    @Test
    @Order(20)
    void editImageWithDataWidgetAttribute(TestUtils setup, TestReference testReference)
    {
        setup.loginAsSuperAdmin();
        ViewPage page = setup.createPage(testReference, "[[image:image.gif||data-widget='uploadimage']]");
        WYSIWYGEditPage wysiwygEditPage = page.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();
        // Make sure the image can be clicked as a proof that the editor did not crash.
        editor.executeOnEditedContent(() -> setup.getDriver().findElement(By.cssSelector("img")).click());
        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();
        assertEquals("[[image:image.gif]]", savedPage.editWiki().getContent());
    }

    @Test
    @Order(21)
    void editLegacyCenteredImageWithLink(TestUtils setup, TestReference testReference) throws Exception
    {
        // Run the tests as a normal user. We make the user advanced only to enable the Edit drop down menu.
        loginStandardUser(setup);

        // Upload an attachment to test with.
        String attachmentName = "image.gif";
        ViewPage newPage = uploadAttachment(setup, testReference, attachmentName);

        WikiEditPage wikiEditPage = newPage.editWiki();
        wikiEditPage.setContent("(% style='text-align: center' %)\n"
            + "[[~[~[image:image.gif~]~]>>Target.Page]]");
        ViewPage viewPage = wikiEditPage.clickSaveAndView();

        // Move to the WYSIWYG edition page.
        WYSIWYGEditPage wysiwygEditPage = viewPage.editWYSIWYG();
        new CKEditor("content").waitToLoad();

        ViewPage savedPage = wysiwygEditPage.clickSaveAndView();

        assertEquals("[[~[~[image:image.gif~|~|data-xwiki-image-style-alignment=\"center\"~]~]>>Target.Page]]",
            savedPage.editWiki().getContent());
    }

    @Test
    @Order(22)
    void uploadImagesFromPastedHTML(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.loginAsSuperAdmin();

        String firstImageName = "image.gif";
        uploadAttachment(setup, testReference, firstImageName);

        String secondImageName = "otherImage.gif";
        ViewPage sourcePage = uploadAttachment(setup, testReference, secondImageName);

        AttachmentReference firstImageReference = new AttachmentReference(firstImageName, testReference);
        String firstImageURL = setup.getURL(firstImageReference, "download", "");

        AttachmentReference secondImageReference = new AttachmentReference(secondImageName, testReference);
        String secondImageURL = setup.getURL(secondImageReference, "download", "x=y");

        // After this method, the clipboard contains an html content with some text and an image.
        copyHTML(setup, sourcePage, """
            <p>
                one <img src="%s"/> two <img src="%s"/> three
            </p>
            """.formatted(firstImageURL, secondImageURL));

        DocumentReference subPageReference = new DocumentReference("Paste", testReference.getLastSpaceReference());
        WYSIWYGEditPage editPage = edit(setup, subPageReference, true);
        this.textArea.clear();
        this.textArea.sendKeys(Keys.chord(Keys.CONTROL, "v"));
        this.textArea.waitForOwnNotificationSuccessMessage("Uploading pasted images: 2 / 2");
        this.textArea.sendKeys(Keys.RIGHT, " after");
        assertSourceEquals("one [[image:image.gif]] two [[image:otherImage.gif]] three after", true);
        editPage.clickSaveAndView();

        AttachmentsPane attachmentsPane = new AttachmentsViewPage().openAttachmentsDocExtraPane();
        assertEquals(2, attachmentsPane.getNumberOfAttachments());
        assertTrue(attachmentsPane.attachmentIsDisplayedByFileName("image.gif"));
        assertTrue(attachmentsPane.attachmentIsDisplayedByFileName("otherImage.gif"));

        //
        // Paste again, but this time cancel the upload.
        //

        editPage = edit(setup, subPageReference, true);
        this.textArea.clear();
        this.textArea.sendKeys(Keys.chord(Keys.CONTROL, "v"));
        this.textArea.waitForOwnNotificationProgressMessage("Starting upload in 4s");
        // Close the notification to cancel the upload.
        this.textArea.sendKeys(Keys.ESCAPE);
        try {
            this.textArea.waitForOwnNotificationSuccessMessage("File successfully uploaded.");
            fail("The upload should have been canceled.");
        } catch (Exception expected) {
            // Expected.
        }
        assertSourceEquals(String.format("one [[image:%s]] two [[image:%s]] three", firstImageURL, secondImageURL),
            true);
        editPage.clickSaveAndView();

        attachmentsPane = new AttachmentsViewPage().openAttachmentsDocExtraPane();
        assertEquals(0, attachmentsPane.getNumberOfAttachments());

        //
        // Paste again, but this time undo the image replacement.
        //

        editPage = edit(setup, subPageReference, true);
        this.textArea.clear();
        this.textArea.sendKeys(Keys.chord(Keys.CONTROL, "v"));
        this.textArea.waitForOwnNotificationSuccessMessage("Uploading pasted images: 2 / 2");
        // Undo the image replacement.
        this.textArea.sendKeys(Keys.chord(Keys.CONTROL, "z"));
        assertSourceEquals(String.format("one [[image:%s]] two [[image:%s]] three", firstImageURL, secondImageURL),
            true);
        editPage.clickSaveAndView();

        attachmentsPane = new AttachmentsViewPage().openAttachmentsDocExtraPane();
        // The image upload is not canceled though (i.e. undo currently operates only at the content level).
        assertEquals(2, attachmentsPane.getNumberOfAttachments());
    }

    @Test
    @Order(23)
    void pasteCaptionedImage(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.loginAsSuperAdmin();

        String firstImageName = "image.gif";
        uploadAttachment(setup, testReference, firstImageName);

        String secondImageName = "otherImage.gif";
        ViewPage sourcePage = uploadAttachment(setup, testReference, secondImageName);

        AttachmentReference firstImageReference = new AttachmentReference(firstImageName, testReference);
        String firstImageURL = setup.getURL(firstImageReference, "download", "");

        AttachmentReference secondImageReference = new AttachmentReference(secondImageName, testReference);
        String secondImageURL = setup.getURL(secondImageReference, "download", "x=y");

        String linkHref = setup.getURL(testReference, "view", "x=y");

        // After this method, the clipboard contains an html content with some text and an image.
        copyHTML(setup, sourcePage, """
            <figure>
                <img src="%s" />
                <figcaption>First image</figcaption>
            </figure>
            <p>between</p>
            <figure>
                <a href="%s">
                  <img src="%s" />
                </a>
                <figcaption>Second image</figcaption>
            </figure>
            """.formatted(firstImageURL, linkHref, secondImageURL));

        DocumentReference subPageReference = new DocumentReference("Paste", testReference.getLastSpaceReference());
        WYSIWYGEditPage editPage = edit(setup, subPageReference, true);
        this.textArea.clear();
        this.textArea.sendKeys(Keys.chord(Keys.CONTROL, "v"));
        this.textArea.sendKeys(Keys.END, " has");
        this.textArea.waitForOwnNotificationSuccessMessage("Uploading pasted images: 2 / 2");
        this.textArea.sendKeys(" caption");
        assertSourceEquals("""
            [[First image>>image:image.gif]]

            between

            [[~[~[Second image has caption~>~>image:otherImage.gif~]~]>>url:%s]]""".formatted(linkHref), true);
        editPage.clickSaveAndView();

        AttachmentsPane attachmentsPane = new AttachmentsViewPage().openAttachmentsDocExtraPane();
        assertEquals(2, attachmentsPane.getNumberOfAttachments());
        assertTrue(attachmentsPane.attachmentIsDisplayedByFileName("image.gif"));
        assertTrue(attachmentsPane.attachmentIsDisplayedByFileName("otherImage.gif"));

        //
        // Paste again, but this time cancel the upload.
        //

        edit(setup, subPageReference, true);
        this.textArea.clear();
        this.textArea.sendKeys(Keys.chord(Keys.CONTROL, "v"));
        this.textArea.waitForOwnNotificationProgressMessage("Starting upload in 4s");
        this.textArea.sendKeys(Keys.END, " is");
        // Close the notification to cancel the upload.
        this.textArea.sendKeys(Keys.ESCAPE);
        this.textArea.sendKeys(" captioned");
        assertSourceEquals("""
            [[First image>>image:%s]]

            between

            [[~[~[Second image is captioned~>~>image:%s~]~]>>url:%s]]""".formatted(firstImageURL, secondImageURL,
            linkHref), true);
    }

    @Test
    @Order(24)
    void moveImageWithinEditedContentWithCopyPaste(TestUtils setup, TestReference testReference) throws Exception
    {
        String imageName = "otherImage.gif";
        DocumentReference childReference = new DocumentReference("childPage", testReference.getLastSpaceReference());
        uploadAttachment(setup, childReference, imageName);

        WYSIWYGEditPage editPage = edit(setup, testReference);
        this.textArea.sendKeys("12 3");

        // Insert the image.
        this.textArea.sendKeys(Keys.LEFT, "/image");
        AutocompleteDropdown qa = new AutocompleteDropdown().waitForItemSelected("/image", "Image");
        this.textArea.sendKeys(Keys.ENTER);
        qa.waitForItemSubmitted();
        this.textArea.sendKeys("other");
        AutocompleteDropdown img = new AutocompleteDropdown().waitForItemSelected("img::other", imageName);
        this.textArea.sendKeys(Keys.ENTER);
        img.waitForItemSubmitted();
        assertSourceEquals("12 [[image:childPage@otherImage.gif]]3");

        // Move the image using cut & paste.
        this.textArea.sendKeys(Keys.LEFT, Keys.LEFT);
        this.textArea.sendKeys(Keys.chord(Keys.SHIFT, Keys.END));
        this.textArea.sendKeys(Keys.chord(Keys.CONTROL, "x"));
        this.textArea.sendKeys(Keys.LEFT);
        this.textArea.sendKeys(Keys.chord(Keys.CONTROL, "v"));

        try {
            this.textArea.waitForOwnNotificationSuccessMessage("Uploading pasted images");
            fail("The editor should not have proposed to upload the image.");
        } catch (Exception e) {
            // Expected.
        }
        assertSourceEquals("2 [[image:childPage@otherImage.gif]]31");

        // Verify that no attachments were uploaded.
        editPage.clickSaveAndView();
        AttachmentsPane attachmentsPane = new AttachmentsViewPage().openAttachmentsDocExtraPane();
        assertEquals(0, attachmentsPane.getNumberOfAttachments());
    }

    @Test
    @IgnoreBrowser(value = "firefox", reason = "We couldn't make the drag & drop work in Firefox inside an iframe "
        + "(the rich text area is implemented using an iframe for the standalone edit mode).")
    @Order(25)
    void moveImageWithinEditedContentWithDragAndDrop(TestUtils setup, TestReference testReference) throws Exception
    {
        String imageName = "otherImage.gif";
        DocumentReference childReference = new DocumentReference("childPage", testReference.getLastSpaceReference());
        uploadAttachment(setup, childReference, imageName);
        WYSIWYGEditPage editPage = edit(setup, testReference);

        // Insert the image.
        this.textArea.sendKeys("/image");
        AutocompleteDropdown qa = new AutocompleteDropdown().waitForItemSelected("/image", "Image");
        this.textArea.sendKeys(Keys.ENTER);
        qa.waitForItemSubmitted();
        this.textArea.sendKeys("other");
        AutocompleteDropdown img = new AutocompleteDropdown().waitForItemSelected("img::other", imageName);
        this.textArea.sendKeys(Keys.ENTER);
        img.waitForItemSubmitted();
        textArea.sendKeys(Keys.RIGHT, "after");
        assertSourceEquals("[[image:childPage@otherImage.gif]]after");

        // Drag the image to the right, after the text.
        this.textArea.dragAndDropImageBy(0, 70, 15);

        try {
            this.textArea.waitForOwnNotificationSuccessMessage("Uploading pasted images");
            fail("The editor should not have proposed to upload the image.");
        } catch (Exception e) {
            // Expected.
        }
        assertSourceEquals("after[[image:childPage@otherImage.gif]]");

        // Verify that no attachments were uploaded.
        editPage.clickSaveAndView();
        AttachmentsPane attachmentsPane = new AttachmentsViewPage().openAttachmentsDocExtraPane();
        assertEquals(0, attachmentsPane.getNumberOfAttachments());
    }

    /**
     * Initialize a page with some HTML content and then, copy its displayed content to the clipboard.
     *
     * @param setup the test setup
     * @param viewPage the page to setup with the given HTML content
     * @param html the HTML content to copy to the clipboard
     */
    private static void copyHTML(TestUtils setup, ViewPage viewPage, String html)
    {
        WikiEditPage wikiEditPage = viewPage.editWiki();
        wikiEditPage.sendKeys("""
                {{html clean="false"}}
                <div contenteditable="true" id="copyme">
                  %s
                </div>
                {{/html}}
            """.formatted(html));
        wikiEditPage.clickSaveAndView();

        WebElement element = setup.getDriver().findElement(By.id("copyme"));
        element.click();
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        element.sendKeys(Keys.chord(Keys.CONTROL, "c"));
    }

    private ViewPage uploadAttachment(TestUtils setup, EntityReference entityReference, String attachmentName)
        throws Exception
    {
        ViewPage newPage = setup.createPage(entityReference, "", "");
        setup.attachFile(entityReference, attachmentName, getClass().getResourceAsStream('/' + attachmentName), false);
        return newPage;
    }

    private static void createBorderedStyle(TestUtils setup)
    {
        DocumentReference borderedStyleDocumentReference =
            new DocumentReference(setup.getCurrentWiki(), List.of("Image", "Style", "Code", "ImageStyles"),
                "borderedPage");
        // For a reason I can't explain, using the rest API lead to random 401 http response, making the tests using the
        // methods flickering. Using the UI based methods until I can understand the root cause.
        setup.deletePage(borderedStyleDocumentReference);
        setup.addObject(borderedStyleDocumentReference, "Image.Style.Code.ImageStyleClass", Map.of(
            "prettyName", "Bordered",
            "type", "bordered",
            "defaultBorder", "1",
            "adjustableAlignment", "1"
        ));
    }

    private static DocumentReference getConfigurationReference(TestUtils setup)
    {
        return new DocumentReference(setup.getCurrentWiki(), List.of("Image", "Style", "Code"), "Configuration");
    }
}
