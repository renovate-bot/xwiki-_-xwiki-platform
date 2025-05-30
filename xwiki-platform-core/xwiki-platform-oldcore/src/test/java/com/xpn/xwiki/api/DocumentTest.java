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
package com.xpn.xwiki.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.internal.document.DocumentRequiredRightsReader;
import org.xwiki.internal.document.RequiredRightClassMandatoryDocumentInitializer;
import org.xwiki.job.event.status.JobProgressManager;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.internal.document.SafeDocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.AuthorizationException;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.sheet.SheetBinder;
import org.xwiki.skin.Skin;
import org.xwiki.skin.SkinManager;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.DocumentRevisionProvider;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.cache.rendering.RenderingCache;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.component.XWikiDocumentFilterUtilsComponentList;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xpn.xwiki.user.api.XWikiRightService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@OldcoreTest
@ReferenceComponentList
@ComponentList({ DocumentRequiredRightsReader.class, RequiredRightClassMandatoryDocumentInitializer.class })
@XWikiDocumentFilterUtilsComponentList
class DocumentTest
{
    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private UserReferenceResolver<CurrentUserReference> currentUserReferenceUserReferenceResolver;

    @MockComponent
    private ObservationManager observationManager;

    // Needed for the mandatory document initializer
    @MockComponent
    private JobProgressManager jobProgressManager;

    @MockComponent
    @Named("document")
    private SheetBinder sheetBinder;

    @MockComponent
    private ContextualLocalizationManager contextualLocalizationManager;

    @MockComponent
    private SkinManager skinManager;

    @MockComponent
    private RenderingCache renderingCache;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.INFO);

    @BeforeEach
    void setUp() throws AuthorizationException, ComponentLookupException
    {
        this.oldcore.getSpyXWiki().initializeMandatoryDocuments(this.oldcore.getXWikiContext());

        DefaultParameterizedType currentUserReferenceResolverType =
            new DefaultParameterizedType(null, UserReferenceResolver.class, CurrentUserReference.class);
        this.currentUserReferenceUserReferenceResolver =
            this.oldcore.getMocker().getInstance(currentUserReferenceResolverType);
    }

    @Test
    void toStringReturnsFullName()
    {
        XWikiDocument doc = new XWikiDocument(new DocumentReference("Wiki", "Space", "Page"));
        assertEquals("Space.Page", new Document(doc, new XWikiContext()).toString());
        assertEquals("Main.WebHome", new Document(new XWikiDocument(), new XWikiContext()).toString());
    }

    @Test
    void getObjects() throws XWikiException
    {
        XWikiContext context = new XWikiContext();
        XWikiDocument doc = new XWikiDocument(new DocumentReference("Wiki", "Space", "Page"));

        doc.getXClass().addNumberField("prop", "prop", 5, "long");
        BaseObject obj = (BaseObject) doc.getXClass().newObject(context);
        obj.setLongValue("prop", 1);
        doc.addObject(doc.getFullName(), obj);

        assertEquals(obj, doc.getObject(doc.getFullName(), "prop", "1"));
        assertNull(doc.getObject(doc.getFullName(), "prop", "2"));

        Document adoc = new Document(doc, context);
        List<Object> lst = adoc.getObjects(adoc.getFullName(), "prop", "1");
        assertEquals(1, lst.size());
        assertEquals(obj, lst.get(0).getBaseObject());

        lst = adoc.getObjects(adoc.getFullName(), "prop", "0");
        assertEquals(0, lst.size());

        lst = adoc.getObjects(adoc.getFullName());
        assertEquals(1, lst.size());
    }

    @Test
    void getObject()
    {
        XWikiContext context = new XWikiContext();
        XWikiDocument doc = new XWikiDocument(new DocumentReference("Wiki", "Space", "Page"));

        doc.getXClass().addNumberField("prop", "prop", 5, "long");

        Document apiDocument = new Document(doc, context);
        ObjectReference objectReference = new ObjectReference("Wiki.Space.Page[2]", doc.getDocumentReference());
        Object apiObject = apiDocument.getObject(objectReference, true);
        apiObject.set("prop", 20);

        assertEquals(apiObject, apiDocument.getObject("Wiki.Space.Page", 2));
        assertEquals(2, apiObject.getNumber());
    }

    @Test
    void removeObjectDoesntCauseDataLoss() throws XWikiException
    {
        // Setup comment class
        XWikiDocument commentDocument = new XWikiDocument(new DocumentReference("wiki", "XWiki", "XWikiComments"));
        commentDocument.getXClass().addTextAreaField("comment", "comment", 60, 20);
        this.oldcore.getSpyXWiki().saveDocument(commentDocument, this.oldcore.getXWikiContext());

        // Setup document
        XWikiDocument xdoc = new XWikiDocument(new DocumentReference("wiki", "Space", "Page"));

        for (int i = 0; i < 10; ++i) {
            xdoc.newXObject(commentDocument.getDocumentReference(), this.oldcore.getXWikiContext());
        }

        Document adoc = xdoc.newDocument(this.oldcore.getXWikiContext());

        for (Object obj : adoc.getObjects("XWiki.XWikiComments")) {
            obj.set("comment", "Comment");
            if (obj.getNumber() == 4) {
                adoc.removeObject(obj);
            }
        }

        // Let's make sure the original document wasn't changed
        for (BaseObject obj : xdoc.getXObjects(commentDocument.getDocumentReference())) {
            assertNull(obj.get("comment"));
        }

        // Let's make sure the cloned document was changed everywhere
        for (BaseObject obj : adoc.getDoc().getXObjects(commentDocument.getDocumentReference())) {
            if (obj != null) {
                assertEquals("Comment", ((BaseProperty) obj.get("comment")).getValue());
            }
        }
    }

    @Test
    void removeAttachment() throws IOException
    {
        XWikiContext context = new XWikiContext();
        XWikiDocument doc = new XWikiDocument(new DocumentReference("Wiki", "Space", "Page"));

        doc.setAttachment("filename.ext", new ByteArrayInputStream("content".getBytes()), context);

        Document apiDocument = new Document(doc, context);

        Attachment removedAttachment = apiDocument.removeAttachment("filename.ext");

        assertNotNull(removedAttachment);
        assertEquals("filename.ext", removedAttachment.getFilename());
        assertNull(apiDocument.getAttachment("filename.ext"));
    }

    @Test
    void saveAsAuthorUsesGuestIfDroppedPermissions() throws XWikiException
    {
        DocumentReference aliceReference = new DocumentReference("wiki", "XWiki", "Alice");
        DocumentReference bobReference = new DocumentReference("wiki", "XWiki", "Bob");

        XWikiDocument cdoc = new XWikiDocument(new DocumentReference("wiki", "Space", "Page"));
        XWikiDocument sdoc = new XWikiDocument(new DocumentReference("wiki", "Space", "AuthorPage"));

        when(this.oldcore.getMockAuthorizationManager().hasAccess(same(Right.EDIT), eq(aliceReference),
            eq(cdoc.getDocumentReference()))).thenReturn(true);
        when(this.oldcore.getMockAuthorizationManager().hasAccess(same(Right.EDIT), isNull(),
            eq(cdoc.getDocumentReference()))).thenReturn(false);

        this.oldcore.getXWikiContext().setDoc(cdoc);
        this.oldcore.getXWikiContext().put("sdoc", sdoc);

        // Alice is the author.
        sdoc.setAuthorReference(aliceReference);
        sdoc.setContentAuthorReference(sdoc.getAuthorReference());

        // Bob is the viewer
        this.oldcore.getXWikiContext().setUserReference(bobReference);

        Document doc = cdoc.newDocument(this.oldcore.getXWikiContext());

        doc.saveAsAuthor();

        this.oldcore.getXWikiContext().dropPermissions();

        Throwable exception = assertThrows(XWikiException.class, () -> doc.saveAsAuthor());
        assertTrue(
            exception.getMessage()
                .contains("Access denied; user null, acting through script in "
                    + "document Space.Page cannot save document Space.Page"),
            "Wrong error message when trying to save a document after calling dropPermissions()");

        assertEquals(bobReference, this.oldcore.getXWikiContext().getUserReference(),
            "After dropping permissions and attempting to save a document, "
                + "the user was permanently switched to guest.");
    }

    @Test
    void user()
    {
        XWikiDocument xdoc = new XWikiDocument(new DocumentReference("Wiki", "Space", "Page"));
        Document document = xdoc.newDocument(this.oldcore.getXWikiContext());

        assertEquals(XWikiRightService.GUEST_USER_FULLNAME, document.getCreator());
        assertEquals(XWikiRightService.GUEST_USER_FULLNAME, document.getAuthor());
        assertEquals(XWikiRightService.GUEST_USER_FULLNAME, document.getContentAuthor());
    }

    @Test
    void changeAuthorWhenModifyingDocumentContent()
    {
        XWikiDocument xdoc = new XWikiDocument(new DocumentReference("wiki0", "Space", "Page"));
        xdoc.setAuthorReference(new DocumentReference("wiki1", "XWiki", "initialauthor"));
        xdoc.setContentAuthorReference(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"));
        xdoc.setCreatorReference(new DocumentReference("wiki1", "XWiki", "initialcreator"));

        this.oldcore.getXWikiContext().setUserReference(new DocumentReference("wiki2", "XWiki", "contextuser"));

        Document document = xdoc.newDocument(this.oldcore.getXWikiContext());

        assertEquals(new DocumentReference("wiki1", "XWiki", "initialauthor"), document.getAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"),
            document.getContentAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcreator"), document.getCreatorReference());

        document.setContent("new content");

        assertEquals(new DocumentReference("wiki2", "XWiki", "contextuser"), document.getAuthorReference());
        assertEquals(new DocumentReference("wiki2", "XWiki", "contextuser"), document.getContentAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcreator"), document.getCreatorReference());
    }

    @Test
    void changeAuthorWhenModifyingObjectProperty() throws XWikiException
    {
        XWikiDocument xdoc = new XWikiDocument(new DocumentReference("wiki0", "Space", "Page"));
        xdoc.setAuthorReference(new DocumentReference("wiki1", "XWiki", "initialauthor"));
        xdoc.setContentAuthorReference(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"));
        xdoc.setCreatorReference(new DocumentReference("wiki1", "XWiki", "initialcreator"));

        xdoc.getXClass().addTextField("key", "Key", 30);
        xdoc.newXObject(xdoc.getDocumentReference(), this.oldcore.getXWikiContext());

        xdoc.setContentDirty(false);
        this.oldcore.getSpyXWiki().saveDocument(xdoc, this.oldcore.getXWikiContext());

        this.oldcore.getXWikiContext().setUserReference(new DocumentReference("wiki2", "XWiki", "contextuser"));

        Document document = xdoc.newDocument(this.oldcore.getXWikiContext());

        assertEquals(new DocumentReference("wiki1", "XWiki", "initialauthor"), document.getAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"),
            document.getContentAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcreator"), document.getCreatorReference());

        Object obj = document.getObject(xdoc.getPrefixedFullName());
        obj.set("key", "value");

        assertEquals(new DocumentReference("wiki2", "XWiki", "contextuser"), document.getAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"),
            document.getContentAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcreator"), document.getCreatorReference());
    }

    @Test
    void changeAuthorWhenModifyingDocumentProperty() throws XWikiException
    {
        XWikiDocument xdoc = new XWikiDocument(new DocumentReference("wiki0", "Space", "Page"));
        xdoc.setAuthorReference(new DocumentReference("wiki1", "XWiki", "initialauthor"));
        xdoc.setContentAuthorReference(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"));
        xdoc.setCreatorReference(new DocumentReference("wiki1", "XWiki", "initialcreator"));

        xdoc.getXClass().addTextField("key", "Key", 30);
        xdoc.newXObject(xdoc.getDocumentReference(), this.oldcore.getXWikiContext());

        xdoc.setContentDirty(false);
        this.oldcore.getSpyXWiki().saveDocument(xdoc, this.oldcore.getXWikiContext());

        this.oldcore.getXWikiContext().setUserReference(new DocumentReference("wiki2", "XWiki", "contextuser"));

        Document document = xdoc.newDocument(this.oldcore.getXWikiContext());

        assertEquals(new DocumentReference("wiki1", "XWiki", "initialauthor"), document.getAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"),
            document.getContentAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcreator"), document.getCreatorReference());

        document.set("key", "value");

        assertEquals(new DocumentReference("wiki2", "XWiki", "contextuser"), document.getAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"),
            document.getContentAuthorReference());
        assertEquals(new DocumentReference("wiki1", "XWiki", "initialcreator"), document.getCreatorReference());
    }

    @ParameterizedTest
    @CsvSource({
        "false, , , true",
        "false, programming, , false",
        "false, script, , true",
        "true, , , false",
        "true, script, script, false",
        "true, script, programming, true",
        "true, programming, script, false"
    })
    void markAsRestrictedWhenNoPR(boolean enforceOnEditedDocument, String rightOnSecureDocument,
        String rightOnEditedDocument, boolean shouldBeRestricted) throws XWikiException
    {
        XWikiDocument secureDocument = new XWikiDocument(new DocumentReference("xwiki", "Space", "Page"));
        DocumentReference secureAuthor = new DocumentReference("wiki1", "XWiki", "secureAuthor");
        secureDocument.setAuthorReference(secureAuthor);
        DocumentReference secureContentAuthor = new DocumentReference("wiki1", "XWiki", "secureContentAuthor");
        secureDocument.setContentAuthorReference(secureContentAuthor);
        secureDocument.setCreatorReference(new DocumentReference("wiki1", "XWiki", "secureCreator"));
        secureDocument.setEnforceRequiredRights(true);
        if (StringUtils.isNotBlank(rightOnSecureDocument)) {
            BaseObject requiredRightObject =
                secureDocument.newXObject(DocumentRequiredRightsReader.CLASS_REFERENCE, this.oldcore.getXWikiContext());
            requiredRightObject.setStringValue(DocumentRequiredRightsReader.PROPERTY_NAME, rightOnSecureDocument);

            Right secureDocumentRight = Right.toRight(rightOnSecureDocument);
            when(this.oldcore.getMockDocumentAuthorizationManager().hasAccess(any(), any(),
                eq(secureContentAuthor), eq(secureDocument.getDocumentReference())))
                .then(invocationOnMock -> {
                    Right requestedRight = invocationOnMock.getArgument(0);
                    return secureDocumentRight.equals(requestedRight) || Right.PROGRAM.equals(secureDocumentRight);
                });
        }
        secureDocument.setContentDirty(false);
        this.oldcore.getSpyXWiki().saveDocument(secureDocument, this.oldcore.getXWikiContext());

        this.oldcore.getXWikiContext()
            .setDoc(this.oldcore.getSpyXWiki().getDocument(secureDocument.getDocumentReference(),
                this.oldcore.getXWikiContext()));

        XWikiDocument editedXDocument = new XWikiDocument(new DocumentReference("xwiki", "Space", "Page1"));
        DocumentReference editedInitialAuthor = new DocumentReference("wiki1", "XWiki", "editedInitialAuthor");
        editedXDocument.setAuthorReference(editedInitialAuthor);
        DocumentReference editedInitialContentAuthor =
            new DocumentReference("wiki1", "XWiki", "editedInitialContentAuthor");
        editedXDocument.setContentAuthorReference(editedInitialContentAuthor);
        editedXDocument.setCreatorReference(new DocumentReference("wiki1", "XWiki", "editedCreator"));
        editedXDocument.setEnforceRequiredRights(enforceOnEditedDocument);
        if (StringUtils.isNotBlank(rightOnEditedDocument)) {
            BaseObject requiredRightObject =
                editedXDocument.newXObject(DocumentRequiredRightsReader.CLASS_REFERENCE,
                    this.oldcore.getXWikiContext());
            requiredRightObject.set(DocumentRequiredRightsReader.PROPERTY_NAME, rightOnEditedDocument,
                this.oldcore.getXWikiContext());
        }
        editedXDocument.setContentDirty(false);
        this.oldcore.getSpyXWiki().saveDocument(editedXDocument, this.oldcore.getXWikiContext());

        Document editedDocument = this.oldcore.getSpyXWiki().getDocument(editedXDocument.getDocumentReference(),
            this.oldcore.getXWikiContext()).newDocument(this.oldcore.getXWikiContext());

        assertEquals(editedInitialAuthor, editedDocument.getAuthorReference());
        assertEquals(editedInitialContentAuthor, editedDocument.getContentAuthorReference());
        assertFalse(editedDocument.isRestricted());

        editedDocument.setContent("Updated content");

        assertEquals(secureContentAuthor, editedDocument.getAuthorReference());
        assertEquals(secureContentAuthor, editedDocument.getContentAuthorReference());
        assertEquals(shouldBeRestricted, editedDocument.isRestricted());
    }

    @Test
    void saveAsAuthorWhenNoPR(MockitoComponentManager componentManager) throws XWikiException, ComponentLookupException
    {
        XWikiDocument xdoc = new XWikiDocument(new DocumentReference("wiki0", "Space", "Page"));
        xdoc.setAuthorReference(new DocumentReference("wiki1", "XWiki", "initialauthor"));
        xdoc.setContentAuthorReference(new DocumentReference("wiki1", "XWiki", "initialcontentauthor"));
        xdoc.setCreatorReference(new DocumentReference("wiki1", "XWiki", "initialcreator"));

        xdoc.setContentDirty(false);
        this.oldcore.getSpyXWiki().saveDocument(xdoc, this.oldcore.getXWikiContext());

        UserReferenceResolver<DocumentReference> userReferenceResolver = componentManager.getInstance(
            new DefaultParameterizedType(null, UserReferenceResolver.class, DocumentReference.class), "document");

        // Set context user
        DocumentReference contextUser = new DocumentReference("wiki2", "XWiki", "contextuser");
        this.oldcore.getXWikiContext().setUserReference(contextUser);
        UserReference userContextReference = userReferenceResolver.resolve(contextUser);
        // Set context author
        XWikiDocument contextDocument = new XWikiDocument("wiki1", "XWiki", "authordocument");
        DocumentReference authorReference = new DocumentReference("wiki3", "XWiki", "contextauthor");
        UserReference userAuthorReference = userReferenceResolver.resolve(authorReference);
        contextDocument.setContentAuthorReference(authorReference);
        this.oldcore.getSpyXWiki().saveDocument(xdoc, this.oldcore.getXWikiContext());
        this.oldcore.getXWikiContext().setDoc(contextDocument);

        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.EDIT, authorReference,
            xdoc.getDocumentReference())).thenReturn(true);
        when(this.oldcore.getMockRightService().hasProgrammingRights(this.oldcore.getXWikiContext())).thenReturn(false);

        Document document = xdoc.newDocument(this.oldcore.getXWikiContext());

        assertEquals(new DocumentReference("wiki1", "XWiki", "initialauthor"), document.getAuthorReference());

        when(this.oldcore.getMockRightService().hasAccessLevel("edit", this.oldcore.getXWikiContext().getUser(),
            document.getPrefixedFullName(), this.oldcore.getXWikiContext())).thenReturn(false);

        assertThrows(XWikiException.class, () -> document.save());

        when(this.oldcore.getMockRightService().hasAccessLevel("edit", this.oldcore.getXWikiContext().getUser(),
            document.getPrefixedFullName(), this.oldcore.getXWikiContext())).thenReturn(true);

        when(this.currentUserReferenceUserReferenceResolver.resolve(CurrentUserReference.INSTANCE))
            .thenReturn(userContextReference)
            .thenReturn(userContextReference)
            .thenReturn(userAuthorReference)
            .thenReturn(userContextReference);
        document.save();

        assertEquals(userContextReference, document.getAuthors().getOriginalMetadataAuthor());
        assertEquals(userAuthorReference, document.getAuthors().getEffectiveMetadataAuthor());

        when(this.oldcore.getMockRightService().hasProgrammingRights(this.oldcore.getXWikiContext())).thenReturn(true);

        document.save();

        assertEquals(userContextReference, document.getAuthors().getEffectiveMetadataAuthor());
    }

    @ParameterizedTest
    @CsvSource({
        "false, , , true, false",
        "false, programming, , false, false",
        "false, script, , true, false",
        "false, script, programming, true, true",
        "true, , , false, false",
        "true, script, script, false, false",
        "true, programming, script, false, false",
        "true, script, programming, false, true"
    })
    void saveWithRequiredRights(boolean enforceOnEditedDocument, String rightOnSecureDocument,
        String rightOnEditedDocument, boolean shouldEnforce, boolean shouldFail)
        throws XWikiException, AccessDeniedException
    {
        XWikiDocument secureDocument = new XWikiDocument(new DocumentReference("xwiki", "Space", "Page"));
        DocumentReference secureAuthor = new DocumentReference("wiki1", "XWiki", "secureAuthor");
        secureDocument.setAuthorReference(secureAuthor);
        DocumentReference secureContentAuthor = new DocumentReference("wiki1", "XWiki", "secureContentAuthor");
        secureDocument.setContentAuthorReference(secureContentAuthor);
        secureDocument.setCreatorReference(new DocumentReference("wiki1", "XWiki", "secureCreator"));
        secureDocument.setEnforceRequiredRights(true);
        if (StringUtils.isNotBlank(rightOnSecureDocument)) {
            BaseObject requiredRightObject =
                secureDocument.newXObject(DocumentRequiredRightsReader.CLASS_REFERENCE, this.oldcore.getXWikiContext());
            requiredRightObject.setStringValue(DocumentRequiredRightsReader.PROPERTY_NAME, rightOnSecureDocument);
        }

        Right secureDocumentRight = Right.toRight(rightOnSecureDocument);
        if (!Right.PROGRAM.equals(secureDocumentRight)) {
            doThrow(new AccessDeniedException(Right.PROGRAM, secureDocument.getDocumentReference(),
                secureContentAuthor))
                .when(this.oldcore.getMockDocumentAuthorizationManager())
                .checkAccess(Right.PROGRAM, null, secureContentAuthor, secureDocument.getDocumentReference());
            if (!Right.SCRIPT.equals(secureDocumentRight)) {
                doThrow(new AccessDeniedException(secureDocumentRight, secureDocument.getDocumentReference(),
                    secureContentAuthor))
                    .when(this.oldcore.getMockDocumentAuthorizationManager())
                    .checkAccess(secureDocumentRight, null, secureContentAuthor, secureDocument.getDocumentReference());
            }
        } else {
            when(this.oldcore.getMockDocumentAuthorizationManager().hasAccess(any(), any(), eq(secureContentAuthor),
                eq(secureDocument.getDocumentReference()))).thenReturn(true);
        }

        secureDocument.setContentDirty(false);
        this.oldcore.getSpyXWiki().saveDocument(secureDocument, this.oldcore.getXWikiContext());

        this.oldcore.getXWikiContext()
            .setDoc(this.oldcore.getSpyXWiki().getDocument(secureDocument.getDocumentReference(),
                this.oldcore.getXWikiContext()));

        XWikiDocument editedXDocument = new XWikiDocument(new DocumentReference("xwiki", "Space", "Page1"));
        DocumentReference editedInitialAuthor = new DocumentReference("wiki1", "XWiki", "editedInitialAuthor");
        editedXDocument.setAuthorReference(editedInitialAuthor);
        DocumentReference editedInitialContentAuthor =
            new DocumentReference("wiki1", "XWiki", "editedInitialContentAuthor");
        editedXDocument.setContentAuthorReference(editedInitialContentAuthor);
        editedXDocument.setCreatorReference(new DocumentReference("wiki1", "XWiki", "editedCreator"));
        editedXDocument.setEnforceRequiredRights(enforceOnEditedDocument);
        if (StringUtils.isNotBlank(rightOnEditedDocument)) {
            BaseObject requiredRightObject =
                editedXDocument.newXObject(DocumentRequiredRightsReader.CLASS_REFERENCE,
                    this.oldcore.getXWikiContext());
            requiredRightObject.set(DocumentRequiredRightsReader.PROPERTY_NAME, rightOnEditedDocument,
                this.oldcore.getXWikiContext());
        }
        editedXDocument.setContentDirty(false);
        this.oldcore.getSpyXWiki().saveDocument(editedXDocument, this.oldcore.getXWikiContext());

        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.EDIT, secureContentAuthor,
            editedXDocument.getDocumentReference())).thenReturn(true);

        Document editedDocument = this.oldcore.getSpyXWiki().getDocument(editedXDocument.getDocumentReference(),
            this.oldcore.getXWikiContext()).newDocument(this.oldcore.getXWikiContext());

        assertEquals(editedInitialAuthor, editedDocument.getAuthorReference());
        assertEquals(editedInitialContentAuthor, editedDocument.getContentAuthorReference());
        assertFalse(editedDocument.isRestricted());

        if (shouldFail) {
            assertThrows(XWikiException.class, () -> editedDocument.saveAsAuthor("Test fail", false));
        } else {
            editedDocument.saveAsAuthor("Test", false);
        }

        if (!enforceOnEditedDocument) {
            assertEquals(shouldEnforce, editedDocument.isEnforceRequiredRights());
        }

        assertEquals(editedDocument.getAuthorReference(), secureContentAuthor);
    }

    @Test
    void getAuthors()
    {
        DocumentAuthors documentAuthors = mock(DocumentAuthors.class);
        AuthorizationManager mockAuthorizationManager = this.oldcore.getMockAuthorizationManager();
        XWikiContext context = this.oldcore.getXWikiContext();
        DocumentReference userReference = new DocumentReference("xwiki", "XWiki", "Foo");
        context.setUserReference(userReference);
        DocumentReference currentDocReference = mock(DocumentReference.class, "currentDocRef");
        XWikiDocument currentDoc = mock(XWikiDocument.class);
        when(currentDoc.getAuthors()).thenReturn(documentAuthors);
        Document document = new Document(currentDoc, context);

        when(currentDoc.getDocumentReference()).thenReturn(currentDocReference);

        when(mockAuthorizationManager.hasAccess(Right.PROGRAM, userReference, currentDocReference)).thenReturn(false);
        DocumentAuthors obtainedAuthors = document.getAuthors();
        assertTrue(obtainedAuthors instanceof SafeDocumentAuthors);
        assertEquals(new SafeDocumentAuthors(documentAuthors), obtainedAuthors);

        verify(mockAuthorizationManager).hasAccess(Right.PROGRAM, userReference, currentDocReference);

        when(mockAuthorizationManager.hasAccess(Right.PROGRAM, userReference, currentDocReference)).thenReturn(true);
        when(currentDoc.clone()).thenReturn(currentDoc);
        obtainedAuthors = document.getAuthors();
        assertSame(documentAuthors, obtainedAuthors);
        verify(mockAuthorizationManager, times(2)).hasAccess(Right.PROGRAM, userReference, currentDocReference);
        verify(currentDoc).clone();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void getDocumentRevision(boolean allowAccess, MockitoComponentManager componentManager) throws Exception
    {
        DocumentReference documentReference = new DocumentReference("Wiki", "Space", "Page");
        XWikiDocument xWikiDocument = new XWikiDocument(documentReference);
        Document document = new Document(xWikiDocument, this.oldcore.getXWikiContext());
        DocumentRevisionProvider revisionProvider =
            componentManager.registerMockComponent(DocumentRevisionProvider.class);
        String revision = "42.1";
        XWikiDocument revisionDocument = mock(XWikiDocument.class);
        when(revisionProvider.getRevision(xWikiDocument, revision)).thenReturn(revisionDocument);
        String deniedMessage = "Denied";
        if (!allowAccess) {
            doThrow(new AuthorizationException(deniedMessage)).when(revisionProvider)
                .checkAccess(Right.VIEW, CurrentUserReference.INSTANCE, documentReference, revision);
            assertNull(document.getDocumentRevision(revision));
            assertEquals(1, this.logCapture.size());
            assertEquals(String.format("Access denied for loading revision [%s] of document [%s()]: "
                    + "[AuthorizationException: %s]", revision,
                documentReference, deniedMessage), this.logCapture.getMessage(0));
        } else {
            assertEquals(new Document(revisionDocument, this.oldcore.getXWikiContext()),
                document.getDocumentRevision(revision));
        }
        verify(revisionProvider).checkAccess(Right.VIEW, CurrentUserReference.INSTANCE, documentReference, revision);
    }

    @Test
    void getXML() throws XWikiException
    {
        Skin skin = mock();
        when(this.skinManager.getCurrentSkin(anyBoolean())).thenReturn(skin);
        when(skin.getOutputSyntax()).thenReturn(Syntax.HTML_5_0);

        XWikiDocument classDocument =
            this.oldcore.getSpyXWiki().getDocument(new DocumentReference("Wiki", "XWiki", "TestClass"),
                this.oldcore.getXWikiContext());
        String passwordField = "secret";
        classDocument.getXClass().addPasswordField(passwordField, "Secret Field", 20);
        String emailField = "contact";
        classDocument.getXClass().addEmailField(emailField, "Contact", 20);
        String textField = "name";
        classDocument.getXClass().addTextField(textField, "Name", 10);
        this.oldcore.getSpyXWiki().saveDocument(classDocument, this.oldcore.getXWikiContext());

        DocumentReference documentReference = new DocumentReference("Wiki", "Space", "Page");
        String content = "content";
        when(this.renderingCache.getRenderedContent(new DocumentReference(documentReference, Locale.ROOT), content,
            this.oldcore.getXWikiContext())).thenReturn("Rendered content");
        XWikiDocument xdoc = new XWikiDocument(documentReference);
        BaseObject object = xdoc.newXObject(classDocument.getDocumentReference(), this.oldcore.getXWikiContext());
        object.set(passwordField, "MySecret", this.oldcore.getXWikiContext());
        object.set(emailField, "hello@example.com", this.oldcore.getXWikiContext());
        object.set(textField, "John", this.oldcore.getXWikiContext());
        xdoc.setContent(content);
        long timestamp = 1740501147000L;
        Date date = new Date(timestamp);
        xdoc.setCreationDate(date);
        xdoc.setContentUpdateDate(date);
        xdoc.setDate(date);
        Document document = new Document(xdoc, this.oldcore.getXWikiContext());
        assertEquals(
            """
                <?xml version='1.1' encoding='UTF-8'?>
                <xwikidoc version="1.6" reference="Space.Page" locale="">
                  <web>Space</web>
                  <name>Page</name>
                  <language/>
                  <defaultLanguage/>
                  <translation>0</translation>
                  <creator>XWiki.XWikiGuest</creator>
                  <creationDate>1740501147000</creationDate>
                  <author>XWiki.XWikiGuest</author>
                  <originalMetadataAuthor>XWiki.XWikiGuest</originalMetadataAuthor>
                  <contentAuthor>XWiki.XWikiGuest</contentAuthor>
                  <date>1740501147000</date>
                  <contentUpdateDate>1740501147000</contentUpdateDate>
                  <version>1.1</version>
                  <title/>
                  <comment/>
                  <minorEdit>false</minorEdit>
                  <syntaxId>xwiki/2.1</syntaxId>
                  <hidden>false</hidden>
                  <content>content</content>
                  <renderedcontent>Rendered content</renderedcontent>
                  <object>
                    <name>Space.Page</name>
                    <number>0</number>
                    <className>XWiki.TestClass</className>
                    <guid>%s</guid>
                    <class>
                      <name>XWiki.TestClass</name>
                      <customClass/>
                      <customMapping/>
                      <defaultViewSheet/>
                      <defaultEditSheet/>
                      <defaultWeb/>
                      <nameField/>
                      <validationScript/>
                      <contact>
                        <disabled>0</disabled>
                        <name>contact</name>
                        <number>2</number>
                        <prettyName>Contact</prettyName>
                        <size>20</size>
                        <unmodifiable>0</unmodifiable>
                        <validationRegExp>/^(([^@\\s]+)@((?:[-a-zA-Z0-9]+\\.)+[a-zA-Z]{2,}))?$/</validationRegExp>
                        <classType>com.xpn.xwiki.objects.classes.EmailClass</classType>
                      </contact>
                      <name>
                        <disabled>0</disabled>
                        <name>name</name>
                        <number>3</number>
                        <prettyName>Name</prettyName>
                        <size>10</size>
                        <unmodifiable>0</unmodifiable>
                        <classType>com.xpn.xwiki.objects.classes.StringClass</classType>
                      </name>
                      <secret>
                        <disabled>0</disabled>
                        <name>secret</name>
                        <number>1</number>
                        <prettyName>Secret Field</prettyName>
                        <size>20</size>
                        <unmodifiable>0</unmodifiable>
                        <classType>com.xpn.xwiki.objects.classes.PasswordClass</classType>
                      </secret>
                    </class>
                    <property>
                      <name>John</name>
                    </property>
                  </object>
                </xwikidoc>""".formatted(object.getGuid()), document.getXMLContent());
    }
}
