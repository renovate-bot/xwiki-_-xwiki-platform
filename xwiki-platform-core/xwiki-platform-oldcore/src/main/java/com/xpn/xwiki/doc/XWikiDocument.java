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
package com.xpn.xwiki.doc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Provider;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.velocity.VelocityContext;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.dom.DOMDocument;
import org.dom4j.io.DocumentResult;
import org.dom4j.io.OutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.suigeneris.jrcs.diff.Diff;
import org.suigeneris.jrcs.diff.DifferentiationFailedException;
import org.suigeneris.jrcs.diff.Revision;
import org.suigeneris.jrcs.diff.delta.Delta;
import org.suigeneris.jrcs.rcs.Version;
import org.suigeneris.jrcs.util.ToString;
import org.xwiki.bridge.DocumentModelBridge;
import org.xwiki.cache.CacheControl;
import org.xwiki.cache.DisposableCacheValue;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContextException;
import org.xwiki.context.ExecutionContextManager;
import org.xwiki.display.internal.DocumentDisplayer;
import org.xwiki.display.internal.DocumentDisplayerParameters;
import org.xwiki.filter.input.DefaultInputStreamInputSource;
import org.xwiki.filter.input.InputSource;
import org.xwiki.filter.input.StringInputSource;
import org.xwiki.filter.instance.input.DocumentInstanceInputProperties;
import org.xwiki.filter.instance.output.DocumentInstanceOutputProperties;
import org.xwiki.filter.output.DefaultOutputStreamOutputTarget;
import org.xwiki.filter.output.DefaultWriterOutputTarget;
import org.xwiki.filter.output.OutputTarget;
import org.xwiki.filter.xar.input.XARInputProperties;
import org.xwiki.filter.xar.output.XAROutputProperties;
import org.xwiki.filter.xml.output.DefaultResultOutputTarget;
import org.xwiki.job.event.status.JobProgressManager;
import org.xwiki.link.LinkException;
import org.xwiki.link.LinkStore;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.LocaleUtils;
import org.xwiki.logging.LoggerConfiguration;
import org.xwiki.model.EntityType;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.internal.document.DefaultDocumentAuthors;
import org.xwiki.model.internal.reference.DefaultSymbolScheme;
import org.xwiki.model.internal.reference.EntityReferenceFactory;
import org.xwiki.model.internal.reference.LocalStringEntityReferenceSerializer;
import org.xwiki.model.internal.reference.LocalUidStringEntityReferenceSerializer;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceProvider;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.ObjectPropertyReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.model.reference.ObjectReferenceResolver;
import org.xwiki.model.reference.PageReference;
import org.xwiki.model.reference.PageReferenceResolver;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.Block.Axes;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.SectionBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.block.match.MacroBlockMatcher;
import org.xwiki.rendering.internal.parser.LinkParser;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.parser.MissingParserException;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.syntax.SyntaxRegistry;
import org.xwiki.rendering.transformation.RenderingContext;
import org.xwiki.rendering.transformation.Transformation;
import org.xwiki.rendering.util.ErrorBlockGenerator;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.stability.Unstable;
import org.xwiki.store.TemporaryAttachmentSessionsManager;
import org.xwiki.store.merge.MergeDocumentResult;
import org.xwiki.store.merge.MergeManager;
import org.xwiki.user.GuestUserReference;
import org.xwiki.user.UserConfiguration;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;
import org.xwiki.user.UserReferenceSerializer;
import org.xwiki.velocity.VelocityContextFactory;
import org.xwiki.velocity.VelocityManager;
import org.xwiki.velocity.XWikiVelocityContext;
import org.xwiki.velocity.XWikiVelocityException;
import org.xwiki.xar.internal.model.XarDocumentModel;
import org.xwiki.xml.XMLUtils;
import org.xwiki.xml.html.HTMLUtils;

import com.xpn.xwiki.CoreConfiguration;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiConstant;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.DocumentSection;
import com.xpn.xwiki.criteria.impl.RevisionCriteria;
import com.xpn.xwiki.doc.merge.MergeConfiguration;
import com.xpn.xwiki.doc.merge.MergeResult;
import com.xpn.xwiki.doc.rcs.XWikiRCSNodeInfo;
import com.xpn.xwiki.internal.cache.rendering.RenderingCache;
import com.xpn.xwiki.internal.doc.BaseObjects;
import com.xpn.xwiki.internal.doc.XWikiAttachmentList;
import com.xpn.xwiki.internal.filter.XWikiDocumentFilterUtils;
import com.xpn.xwiki.internal.render.OldRendering;
import com.xpn.xwiki.internal.xml.DOMXMLWriter;
import com.xpn.xwiki.internal.xml.XMLWriter;
import com.xpn.xwiki.objects.BaseCollection;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseObjectReference;
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.objects.LargeStringProperty;
import com.xpn.xwiki.objects.ListProperty;
import com.xpn.xwiki.objects.ObjectDiff;
import com.xpn.xwiki.objects.PropertyInterface;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.ListClass;
import com.xpn.xwiki.objects.classes.PropertyClass;
import com.xpn.xwiki.objects.classes.StaticListClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;
import com.xpn.xwiki.store.AttachmentRecycleBinStore;
import com.xpn.xwiki.store.XWikiAttachmentStoreInterface;
import com.xpn.xwiki.store.XWikiHibernateAttachmentStore;
import com.xpn.xwiki.store.XWikiStoreInterface;
import com.xpn.xwiki.store.XWikiVersioningStoreInterface;
import com.xpn.xwiki.user.api.XWikiRightService;
import com.xpn.xwiki.util.Util;
import com.xpn.xwiki.validation.XWikiValidationInterface;
import com.xpn.xwiki.validation.XWikiValidationStatus;
import com.xpn.xwiki.web.EditForm;
import com.xpn.xwiki.web.ObjectAddForm;
import com.xpn.xwiki.web.ObjectPolicyType;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.web.XWikiRequest;

public class XWikiDocument implements DocumentModelBridge, Cloneable, DisposableCacheValue
{
    private static final Logger LOGGER = LoggerFactory.getLogger(XWikiDocument.class);

    private static final String TM_FAILEDDOCUMENTPARSE = "core.document.error.failedParse";

    private static final String[] HTML_MACRO_SEARCH_STRINGS = new String[] { "{{html", "{{/html" };

    private static final String[] HTML_MACRO_REPLACE_STRINGS = new String[] { "&#123;&#123;html", "&#123;&#123;/html" };

    /**
     * An attachment waiting to be deleted at next document save.
     *
     * @version $Id$
     * @since 5.2M1
     */
    public static class XWikiAttachmentToRemove
    {
        /**
         * @see #getAttachment()
         */
        private XWikiAttachment attachment;

        /**
         * @see #isToRecycleBin()
         */
        private boolean toRecycleBin;

        /**
         * @param attachment the attachment to delete
         * @param toRecycleBin true of the attachment should be moved to the recycle bin
         */
        public XWikiAttachmentToRemove(XWikiAttachment attachment, boolean toRecycleBin)
        {
            this.attachment = attachment;
            this.toRecycleBin = toRecycleBin;
        }

        /**
         * @return the attachment to delete
         */
        public XWikiAttachment getAttachment()
        {
            return this.attachment;
        }

        /**
         * @return true of the attachment should be moved to the recycle bin
         */
        public boolean isToRecycleBin()
        {
            return this.toRecycleBin;
        }

        @Override
        public int hashCode()
        {
            return this.attachment.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof XWikiAttachmentToRemove) {
                return this.attachment.equals(((XWikiAttachmentToRemove) obj).getAttachment());
            }

            return false;
        }

        @Override
        public String toString()
        {
            return this.attachment.toString();
        }
    }

    /**
     * Regex Pattern to recognize if there's HTML code in a XWiki page.
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
        "</?+(html|img|a|i|br?|embed|script|form|input|textarea|object|font|li|[dou]l|table|center|hr|p) ?([^>]*+)>");

    public static final EntityReference COMMENTSCLASS_REFERENCE = new LocalDocumentReference("XWiki", "XWikiComments");

    public static final EntityReference SHEETCLASS_REFERENCE = new LocalDocumentReference("XWiki", "SheetClass");

    public static final int HAS_ATTACHMENTS = 1;

    public static final int HAS_OBJECTS = 2;

    public static final int HAS_CLASS = 4;

    /**
     * The name of the key in the {@link XWikiContext} which contains the document used to check for programming rights.
     */
    public static final String CKEY_SDOC = "sdoc";

    /**
     * The name of the key in the {@link XWikiContext} which contains the current content document.
     * 
     * @since 15.9RC1
     */
    public static final String CKEY_CDOC = "cdoc";

    /**
     * The name of the key in the {@link XWikiContext} which contains the current translation document.
     * 
     * @since 15.9RC1
     */
    public static final String CKEY_TDOC = "tdoc";

    /**
     * Separator string between database name and space name.
     */
    public static final String DB_SPACE_SEP = ":";

    /**
     * Separator string between space name and page name.
     */
    public static final String SPACE_NAME_SEP = ".";

    private static final LocalStringEntityReferenceSerializer LOCAL_REFERENCE_SERIALIZER =
        new LocalStringEntityReferenceSerializer(new DefaultSymbolScheme());

    /**
     * Used to resolve a string into a proper Document Reference using the current document's reference to fill the
     * blanks.
     */
    private static DocumentReferenceResolver<String> getCurrentDocumentReferenceResolver()
    {
        return Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, "current");
    }

    /**
     * Used to resolve a ResourceReference into a proper Entity Reference using the current document to fill the blanks.
     */
    private static EntityReferenceResolver<ResourceReference> getResourceReferenceEntityReferenceResolver()
    {
        return Utils
            .getComponent(new DefaultParameterizedType(null, EntityReferenceResolver.class, ResourceReference.class));
    }

    private static EntityReferenceResolver<String> getXClassEntityReferenceResolver()
    {
        return Utils.getComponent(EntityReferenceResolver.TYPE_STRING, "xclass");
    }

    /**
     * Used to resolve a string into a proper Document Reference using the current document's reference to fill the
     * blanks, except for the page name for which the default page name is used instead and for the wiki name for which
     * the current wiki is used instead of the current document reference's wiki.
     */
    private static DocumentReferenceResolver<String> getCurrentMixedDocumentReferenceResolver()
    {
        return Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, "currentmixed");
    }

    /**
     * Used to normalize references.
     */
    private static DocumentReferenceResolver<EntityReference> getCurrentReferenceDocumentReferenceResolver()
    {
        return Utils.getComponent(DocumentReferenceResolver.TYPE_REFERENCE, "current");
    }

    /**
     * Used to resolve parent references in the way they are stored externally (database, xml, etc), ie relative or
     * absolute.
     */
    private static EntityReferenceResolver<String> getRelativeEntityReferenceResolver()
    {
        return Utils.getComponent(EntityReferenceResolver.TYPE_STRING, "relative");
    }

    /**
     * Used to convert a proper Document Reference to string (compact form).
     */
    private static EntityReferenceSerializer<String> getCompactEntityReferenceSerializer()
    {
        return Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, "compact");
    }

    /**
     * Used to convert a Document Reference to string (compact form without the wiki part if it matches the current
     * wiki).
     */
    private static EntityReferenceSerializer<String> getCompactWikiEntityReferenceSerializer()
    {
        return Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, "compactwiki");
    }

    /**
     * Used to normalize references.
     */
    private static ObjectReferenceResolver<EntityReference> getCurrentReferenceObjectReferenceResolver()
    {
        return Utils.getComponent(ObjectReferenceResolver.TYPE_REFERENCE, "current");
    }

    /**
     * Used to convert a syntax defined as String into a Syntax object.
     */
    private static SyntaxRegistry getSyntaxRegistry()
    {
        return Utils.getComponent(SyntaxRegistry.class);
    }

    /**
     * Used to retrieve backlinks from an XDOM.
     */
    private static LinkParser getLinkParser()
    {
        return Utils.getComponent(LinkParser.class);
    }

    /**
     * @return the user module configuration, used for instance to determine where users are stored
     */
    private static UserConfiguration getUserConfiguration()
    {
        return Utils.getComponent(UserConfiguration.class);
    }

    private static CacheControl getCacheControl()
    {
        return Utils.getComponent(CacheControl.class);
    }

    private static Transformation getMacroTransformation()
    {
        return Utils.getComponent(Transformation.class, "macro");
    }

    private String title;

    private Object preparedTitle;

    private volatile LocalDateTime titlePrepareDate;

    /**
     * Reference to this document's parent.
     * <p>
     * Note that we're saving the parent reference as a relative reference instead of an absolute one because We want
     * the ability (for example) to create a parent reference relative to the current space or wiki so that a copy of
     * this XWikiDocument object would retain that relativity. This is for example useful when copying a Wiki into
     * another Wiki so that the copied XWikiDcoument's parent reference points to the new wiki.
     */
    private EntityReference parentReference;

    private DocumentReference documentReference;

    private class Content
    {
        final String content;

        /**
         * Wiki syntax supported by this document. This is used to support different syntaxes inside the same wiki. For
         * example a page can use the MediaWiki 1.0 syntax while another one uses the XWiki 2.1 syntax.
         * <p>
         * This variable is nullable because it's not possible to get the default syntax in the XWikiDocument
         * constructor right now.
         */
        Syntax syntax;

        XDOM xdom;

        volatile LocalDateTime prepareDate;

        Content(String content, Syntax syntax)
        {
            this.content = StringUtils.defaultString(content);
            this.syntax = syntax;
        }
    }

    /**
     * The document structure expressed as a tree of Block objects. We store it for performance reasons since parsing is
     * a costly operation that we don't want to repeat whenever some code ask for the XDOM information.
     */
    private Content content = new Content("", null);

    private String meta;

    private String format;

    private String customClass;

    private Date contentUpdateDate;

    private Date updateDate;

    private Date creationDate;

    protected Version version;

    private long id = 0;

    private boolean mostRecent = true;

    private boolean isNew = true;

    /**
     * The reference to the document that is the template for the current document.
     *
     * @todo this field is not used yet since it's not currently saved in the database.
     */
    private DocumentReference templateDocumentReference;

    private Locale locale;

    private Locale defaultLocale;

    /**
     * Indicates whether the document is 'hidden', meaning that it should not be returned in public search results.
     * WARNING: this is a temporary hack until the new data model is designed and implemented. No code should rely on or
     * use this property, since it will be replaced with a generic metadata.
     */
    private boolean hidden = false;

    /**
     * Comment on the latest modification.
     */
    private String comment;

    /**
     * If required rights that can be defined in a {@code XWiki.RequiredRightClass} shall be enforced. For
     * backwards-compatibility reasons, this is {@code false} by default.
     * @since 16.10.0RC1
     */
    private boolean enforceRequiredRights = false;

    /**
     * Is latest modification a minor edit.
     */
    private boolean isMinorEdit = false;

    /**
     * Used to make sure the MetaData String is regenerated.
     */
    private boolean isContentDirty = true;

    /**
     * Used to make sure the MetaData String is regenerated.
     */
    private boolean isMetaDataDirty = true;

    private boolean changeTracked;

    private int elements = HAS_OBJECTS | HAS_ATTACHMENTS;

    // Meta Data
    private BaseClass xClass;

    private String xClassXML;

    /**
     * Map holding document objects indexed by XClass references (i.e. Document References since a XClass reference
     * points to a document). The preserve index ordering (consistent sorted order for output to XML, rendering in
     * velocity, etc.)
     */
    private Map<DocumentReference, BaseObjects> xObjects = new ConcurrentSkipListMap<>();

    /**
     * The publicly exposed Map.
     */
    private Map<DocumentReference, List<BaseObject>> publicXObjects = new Map<DocumentReference, List<BaseObject>>()
    {
        @Override
        public List<BaseObject> put(DocumentReference key, List<BaseObject> value)
        {
            // Makes sure to always insert BaseObjects
            return xObjects.put(key, value instanceof BaseObjects ? (BaseObjects) value : new BaseObjects(value));
        }

        @Override
        public void putAll(Map<? extends DocumentReference, ? extends List<BaseObject>> m)
        {
            m.forEach(this::put);
        }

        @Override
        public int size()
        {
            return xObjects.size();
        }

        @Override
        public boolean isEmpty()
        {
            return xObjects.isEmpty();
        }

        @Override
        public boolean containsKey(Object key)
        {
            return xObjects.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value)
        {
            return xObjects.containsValue(value);
        }

        @Override
        public List<BaseObject> get(Object key)
        {
            return xObjects.get(key);
        }

        @Override
        public List<BaseObject> remove(Object key)
        {
            return xObjects.remove(key);
        }

        @Override
        public void clear()
        {
            xObjects.clear();
        }

        @Override
        public Set<DocumentReference> keySet()
        {
            return xObjects.keySet();
        }

        @Override
        public Collection<List<BaseObject>> values()
        {
            return (Collection) xObjects.values();
        }

        @Override
        public Set<Entry<DocumentReference, List<BaseObject>>> entrySet()
        {
            return (Set) xObjects.entrySet();
        }
    };

    private final XWikiAttachmentList attachmentList = new XWikiAttachmentList(XWikiDocument.this);

    // Caching
    private boolean fromCache = false;

    private boolean cached;

    private List<BaseObject> xObjectsToRemove = new ArrayList<BaseObject>();

    private List<XWikiAttachmentToRemove> attachmentsToRemove = new ArrayList<XWikiAttachmentToRemove>();

    /**
     * The view template (vm file) to use. When not set the default view template is used.
     *
     * @see com.xpn.xwiki.web.ViewAction#render(XWikiContext)
     */
    private String defaultTemplate;

    private String validationScript;

    private Object wikiNode;

    /**
     * We are using a SoftReference which will allow the archive to be discarded by the Garbage collector as long as the
     * context is closed (usually during the request)
     */
    private SoftReference<XWikiDocumentArchive> archive;

    private XWikiStoreInterface store;

    /**
     * @see #getOriginalDocument()
     */
    private XWikiDocument originalDocument;

    /**
     * If the document should always be rendered in restricted mode.
     */
    private boolean restricted;

    /**
     * Used to display the title and the content of this document. Do not inject the component here to avoid any simple
     * new XWikiDocument to cause many useless initialization, in particular, during initialization of the stub context
     * and other fake documents in tests.
     */
    private DocumentDisplayer documentDisplayer;

    /**
     * @see #getDefaultEntityReferenceSerializer()
     */
    private EntityReferenceSerializer<String> defaultEntityReferenceSerializer;

    /**
     * @see #getExplicitDocumentReferenceResolver()
     */
    private DocumentReferenceResolver<String> explicitDocumentReferenceResolver;

    /**
     * @see #getExplicitReferenceDocumentReferenceResolver()
     */
    private DocumentReferenceResolver<EntityReference> explicitReferenceDocumentReferenceResolver;

    /**
     * @see #getPageReferenceResolver()
     */
    private PageReferenceResolver<EntityReference> pageReferenceResolver;

    /**
     * @see #getUidStringEntityReferenceSerializer()
     */
    private EntityReferenceSerializer<String> uidStringEntityReferenceSerializer;

    private Provider<OldRendering> oldRenderingProvider;

    private JobProgressManager progress;

    private ContextualLocalizationManager localization;

    private VelocityContextFactory velocityContextFactory;

    private EntityReferenceFactory entityReferenceFactory;

    /**
     * Use to store rendered documents in #getRenderedContent(). Do not inject the component here to avoid any simple
     * new XWikiDocument to cause many useless initialization, in particular, during initialization of the stub context
     * and other fake documents in tests.
     */
    private RenderingCache renderingCache;

    /**
     * Cache the parent reference resolved as an absolute reference for improved performance (so that we don't have to
     * resolve the relative reference every time getParentReference() is called.
     */
    private DocumentReference parentReferenceCache;

    /**
     * Cache the document reference with locale resolved kept for improved performance (so that we don't have to resolve
     * it every time getPageReference() is called.
     */
    private DocumentReference documentReferenceWithLocaleCache;

    /**
     * Cache the page reference resolved kept for improved performance (so that we don't have to resolve it every time
     * getPageReference() is called.
     */
    private PageReference pageReferenceCache;

    /**
     * Cache the page reference with locale resolved kept for improved performance (so that we don't have to resolve it
     * every time getPageReference() is called.
     */
    private PageReference pageReferenceWithLocaleCache;

    /**
     * @see #getKey()
     */
    private String keyCache;

    /**
     * @see #getLocalKey()
     */
    private String localKeyCache;

    private RenderingContext renderingContext;

    /**
     * @see #getAuthors()
     */
    private final DefaultDocumentAuthors authors = new DefaultDocumentAuthors(this);

    /**
     * Create a document for the given reference, with the {@link Locale#ROOT} even if the reference contains a locale.
     * If you want to create a document for another locale, use {@link #XWikiDocument(DocumentReference, Locale)}.
     * 
     * @since 2.2M1
     */
    public XWikiDocument(DocumentReference reference)
    {
        init(reference);
    }

    /**
     * @since 6.2
     */
    public XWikiDocument(DocumentReference reference, Locale locale)
    {
        init(reference);

        this.locale = locale;
    }

    /**
     * @deprecated use {@link #XWikiDocument(org.xwiki.model.reference.DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public XWikiDocument()
    {
        this(null);
    }

    /**
     * Constructor that specifies the local document identifier: space name, document name. {@link #setDatabase(String)}
     * must be called afterwards to specify the wiki name.
     *
     * @param space the space this document belongs to
     * @param name the name of the document
     * @deprecated use {@link #XWikiDocument(org.xwiki.model.reference.DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public XWikiDocument(String space, String name)
    {
        this(null, space, name);
    }

    /**
     * Constructor that specifies the full document identifier: wiki name, space name, document name.
     *
     * @param wiki The wiki this document belongs to.
     * @param space The space this document belongs to.
     * @param name The name of the document (can contain either the page name or the space and page name)
     * @deprecated use {@link #XWikiDocument(org.xwiki.model.reference.DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public XWikiDocument(String wiki, String space, String name)
    {
        // We allow to specify the space in the name (eg name = "space.page"). In this case the passed space is
        // ignored.

        // Build an entity reference that will serve as a current context reference against which to resolve if the
        // passed name doesn't contain a space.
        EntityReference contextReference = null;
        if (!StringUtils.isEmpty(space)) {
            contextReference = new EntityReference(space, EntityType.SPACE);
        }

        DocumentReference reference = getCurrentDocumentReferenceResolver().resolve(name, contextReference);

        if (!StringUtils.isEmpty(wiki)) {
            reference = reference.replaceParent(reference.getWikiReference(), new WikiReference(wiki));
        }

        init(reference);
    }

    @Override
    public void dispose() throws Exception
    {
        setCached(false);
    }

    /**
     * Used to resolve a string into a proper Document Reference.
     */
    private DocumentReferenceResolver<String> getExplicitDocumentReferenceResolver()
    {
        if (this.explicitDocumentReferenceResolver == null) {
            this.explicitDocumentReferenceResolver =
                Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, "explicit");
        }

        return this.explicitDocumentReferenceResolver;
    }

    /**
     * Used to normalize references.
     */
    private DocumentReferenceResolver<EntityReference> getExplicitReferenceDocumentReferenceResolver()
    {
        if (this.explicitReferenceDocumentReferenceResolver == null) {
            this.explicitReferenceDocumentReferenceResolver =
                Utils.getComponent(DocumentReferenceResolver.TYPE_REFERENCE, "explicit");
        }

        return this.explicitReferenceDocumentReferenceResolver;
    }

    private PageReferenceResolver<EntityReference> getPageReferenceResolver()
    {
        if (this.pageReferenceResolver == null) {
            this.pageReferenceResolver = Utils.getComponent(PageReferenceResolver.TYPE_REFERENCE);
        }

        return this.pageReferenceResolver;
    }

    /**
     * Used to convert a proper Document Reference to string (standard form).
     */
    private EntityReferenceSerializer<String> getDefaultEntityReferenceSerializer()
    {
        if (this.defaultEntityReferenceSerializer == null) {
            this.defaultEntityReferenceSerializer = Utils.getComponent(EntityReferenceSerializer.TYPE_STRING);
        }

        return this.defaultEntityReferenceSerializer;
    }

    /**
     * Used to compute document identifier.
     */
    private EntityReferenceSerializer<String> getUidStringEntityReferenceSerializer()
    {
        if (this.uidStringEntityReferenceSerializer == null) {
            this.uidStringEntityReferenceSerializer = Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, "uid");
        }

        return this.uidStringEntityReferenceSerializer;
    }

    private ContextualLocalizationManager getLocalization()
    {
        if (this.localization == null) {
            this.localization = Utils.getComponent(ContextualLocalizationManager.class);
        }

        return this.localization;
    }

    private OldRendering getOldRendering()
    {
        if (this.oldRenderingProvider == null) {
            this.oldRenderingProvider = Utils.getComponent(OldRendering.TYPE_PROVIDER);
        }

        return this.oldRenderingProvider.get();
    }

    private JobProgressManager getProgress()
    {
        if (this.progress == null) {
            this.progress = Utils.getComponent(JobProgressManager.class);
        }

        return this.progress;
    }

    private VelocityContextFactory getVelocityContextFactory()
    {
        if (this.velocityContextFactory == null) {
            this.velocityContextFactory = Utils.getComponent(VelocityContextFactory.class);
        }

        return this.velocityContextFactory;
    }

    private EntityReferenceFactory getEntityReferenceFactory()
    {
        if (this.entityReferenceFactory == null && Utils.getRootComponentManager() != null) {
            try {
                this.entityReferenceFactory = Utils.getRootComponentManager().getInstance(EntityReferenceFactory.class);
            } catch (ComponentLookupException e) {
                // Not a big deal
            }
        }

        return this.entityReferenceFactory;
    }

    private <E extends EntityReference> E intern(E reference)
    {
        EntityReferenceFactory factory = getEntityReferenceFactory();

        return factory != null ? factory.getReference(reference) : reference;
    }

    private String localizePlainOrKey(String key, Object... parameters)
    {
        return StringUtils.defaultString(getLocalization().getTranslationPlain(key, parameters), key);
    }

    private UserReferenceSerializer<DocumentReference> getUserReferenceDocumentReferenceSerializer()
    {
        return Utils.getComponent(UserReferenceSerializer.TYPE_DOCUMENT_REFERENCE, "document");
    }

    private UserReferenceResolver<DocumentReference> getUserReferenceDocumentReferenceResolver()
    {
        return Utils.getComponent(UserReferenceResolver.TYPE_DOCUMENT_REFERENCE, "document");
    }

    private UserReferenceSerializer<String> getUserReferenceStringSerializer()
    {
        return Utils.getComponent(UserReferenceSerializer.TYPE_STRING);
    }

    private UserReferenceResolver<String> getUserReferenceStringResolver()
    {
        return Utils.getComponent(UserReferenceResolver.TYPE_STRING);
    }

    private UserReferenceSerializer<String> getUserReferenceCompactWikiSerializer()
    {
        return Utils.getComponent(UserReferenceSerializer.TYPE_STRING, "compactwiki/document");
    }

    private LinkStore getLinkStore()
    {
        return Utils.getComponent(LinkStore.class);
    }

    public XWikiStoreInterface getStore(XWikiContext context)
    {
        return context.getWiki().getStore();
    }

    /**
     * @deprecated use {@link XWiki#getDefaultAttachmentContentStore()} instead
     */
    @Deprecated(since = "9.9RC1")
    public XWikiAttachmentStoreInterface getAttachmentStore(XWikiContext context)
    {
        return context.getWiki().getAttachmentStore();
    }

    public XWikiVersioningStoreInterface getVersioningStore(XWikiContext context)
    {
        return context.getWiki().getVersioningStore();
    }

    public XWikiStoreInterface getStore()
    {
        return this.store;
    }

    public void setStore(XWikiStoreInterface store)
    {
        this.store = store;
    }

    private RenderingContext getRenderingContext()
    {
        if (this.renderingContext == null) {
            this.renderingContext = Utils.getComponent(RenderingContext.class);
        }

        return this.renderingContext;
    }

    /**
     * Helper to produce and cache a local uid serialization of this document reference, including the language. Only
     * translated document will have language appended.
     *
     * @return a unique name (in a wiki) (5:space4:name2:lg)
     */
    private String getLocalKey()
    {
        if (this.localKeyCache == null) {
            this.localKeyCache =
                LocalUidStringEntityReferenceSerializer.INSTANCE.serialize(getDocumentReferenceWithLocale());
        }

        return this.localKeyCache;
    }

    /**
     * Helper to produce and cache a uid serialization of this document reference, including the language. Only
     * translated document will have language appended.
     *
     * @return a unique name (8:wikiname5:space4:name2:lg or 8:wikiname5:space4:name)
     * @since 4.0M1
     */
    public String getKey()
    {
        if (this.keyCache == null) {
            this.keyCache = getUidStringEntityReferenceSerializer().serialize(getDocumentReferenceWithLocale());
        }

        return this.keyCache;
    }

    @Override
    public int hashCode()
    {
        return (int) Util.getHash(getLocalKey());
    }

    /**
     * @return the unique id used to represent the document, as a number. This id is technical and is equivalent to the
     *         Document Reference + the language of the Document. This technical id should only be used for the storage
     *         layer and all user APIs should instead use Document Reference and language as they are model-related
     *         while the id isn't (it's purely technical).
     */
    public long getId()
    {
        // TODO: Ensure uniqueness of the generated id
        // The implementation doesn't guarantee a unique id since it uses a hashing method which never guarantee
        // uniqueness. However, the hash algorithm is really unlikely to collide in a given wiki. This needs to be
        // fixed to produce a real unique id since otherwise we can have clashes in the database.

        // Note: We don't use the wiki name in the document id's computation. The main historical reason is so
        // that all things saved in a given wiki's database are always stored relative to that wiki so that
        // changing that wiki's name is simpler.

        this.id = Util.getHash(getLocalKey());

        return this.id;
    }

    /**
     * @see #getId()
     */
    public void setId(long id)
    {
        this.id = id;
    }

    /**
     * Return the full local space reference. For example a document located in sub-space <code>space11</code> of space
     * <code>space1</code> will return <code>space1.space11</code>.
     * <p>
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @return the local reference the space of the document as String
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    public String getSpace()
    {
        return LOCAL_REFERENCE_SERIALIZER.serialize(getDocumentReference().getLastSpaceReference());
    }

    /**
     * Set the full local space reference.
     * <p>
     * Note that this method cannot be removed for now since it's used by Hibernate for loading a XWikiDocument.
     *
     * @see #getSpace()
     * @deprecated use {@link #setDocumentReference(DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public void setSpace(String spaces)
    {
        if (spaces != null) {
            DocumentReference reference = getDocumentReference();
            EntityReference spaceReference = getRelativeEntityReferenceResolver().resolve(spaces, EntityType.SPACE);
            spaceReference = spaceReference.appendParent(getDocumentReference().getWikiReference());
            setDocumentReferenceInternal(
                new DocumentReference(reference.getName(), new SpaceReference(spaceReference)));
        }
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @return the name of the space of the document
     * @see #getSpace()
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated
    public String getWeb()
    {
        return getSpace();
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for loading a XWikiDocument.
     *
     * @deprecated use {@link #setDocumentReference(DocumentReference)} instead
     */
    @Deprecated
    public void setWeb(String space)
    {
        setSpace(space);
    }

    @Override
    public String getVersion()
    {
        return getRCSVersion().toString();
    }

    public void setVersion(String version)
    {
        if (!StringUtils.isEmpty(version)) {
            this.version = new Version(version);
        }
    }

    public Version getRCSVersion()
    {
        if (this.version == null) {
            return new Version("1.1");
        }
        return this.version;
    }

    public void setRCSVersion(Version version)
    {
        this.version = version;
    }

    /**
     * @return the copy of this XWikiDocument instance before any modification was made to it. This copy is used for
     *         finding out differences made to this document (useful for example to send the correct notifications to
     *         document change listeners).
     */
    @Override
    public XWikiDocument getOriginalDocument()
    {
        return this.originalDocument;
    }

    /**
     * @param originalDocument the original document representing this document instance before any change was made to
     *            it, prior to the last time it was saved
     * @see #getOriginalDocument()
     */
    public void setOriginalDocument(XWikiDocument originalDocument)
    {
        this.originalDocument = originalDocument;
    }

    /**
     * @return the parent reference or null if the parent is not set
     * @since 2.2M1
     */
    public DocumentReference getParentReference()
    {
        // Ensure we always return absolute document references for the parent since we always want well-constructed
        // references and since we store the parent reference as relative internally.
        if (this.parentReferenceCache == null && getRelativeParentReference() != null) {
            this.parentReferenceCache = intern(getExplicitReferenceDocumentReferenceResolver()
                .resolve(getRelativeParentReference(), getDocumentReference()));
        }

        return this.parentReferenceCache;
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @return the parent reference stored in the database, which is relative to this document, or an empty string ("")
     *         if the parent is not set
     * @see #getParentReference()
     * @deprecated use {@link #getParentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    public String getParent()
    {
        String parentReferenceAsString;
        if (getParentReference() != null) {
            parentReferenceAsString = getDefaultEntityReferenceSerializer().serialize(getRelativeParentReference());
        } else {
            parentReferenceAsString = "";
        }
        return parentReferenceAsString;
    }

    /**
     * @deprecated use {@link #getParentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    public XWikiDocument getParentDoc()
    {
        return new XWikiDocument(getParentReference());
    }

    /**
     * @since 2.2.3
     */
    public void setParentReference(EntityReference parentReference)
    {
        if (!Objects.equals(getRelativeParentReference(), parentReference)) {
            this.parentReference = intern(parentReference);

            // Clean the absolute parent reference cache to rebuild it next time getParentReference is called.
            this.parentReferenceCache = null;

            setMetaDataDirty(true);
        }
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for loading a XWikiDocument.
     *
     * @param parent the reference of the parent relative to the document
     * @deprecated use {@link #setParentReference(EntityReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public void setParent(String parent)
    {
        // If the passed parent is an empty string we also need to set the reference to null. The reason is that
        // in the database we store "" when the parent is empty and thus when Hibernate loads this class it'll call
        // setParent with "" if the parent had not been set when saved.
        if (StringUtils.isEmpty(parent)) {
            setParentReference((EntityReference) null);
        } else {
            setParentReference(getRelativeEntityReferenceResolver().resolve(parent, EntityType.DOCUMENT));
        }
    }

    @Override
    public String getContent()
    {
        return this.content.content;
    }

    public void setContent(String content)
    {
        setContent(content, this.content.syntax);
    }

    public void setContent(XDOM content) throws XWikiException
    {
        setContent(renderXDOM(content, getSyntax()));
    }

    /**
     * @param content the content of the document
     * @param syntax the syntax of the content
     * @since 17.2.0
     * @since 16.10.6
     */
    public void setContent(String content, Syntax syntax)
    {
        if (content == null) {
            content = "";
        }

        if (!content.equals(this.content.content) || !Objects.equals(syntax, this.content.syntax)) {
            this.content = new Content(content, syntax);

            setContentDirty(true);
            setWikiNode(null);
        }
    }

    /**
     * @return the default rendering cache
     */
    private RenderingCache getRenderingCache()
    {
        if (this.renderingCache == null) {
            this.renderingCache = Utils.getComponent((Type) RenderingCache.class);
        }
        return this.renderingCache;
    }

    /**
     * @return the configured document displayer
     */
    private DocumentDisplayer getDocumentDisplayer()
    {
        if (this.documentDisplayer == null) {
            this.documentDisplayer = Utils.getComponent((Type) DocumentDisplayer.class, "configured");
        }
        return this.documentDisplayer;
    }

    private Syntax getOutputSyntax()
    {
        return getRenderingContext().getTargetSyntax();
    }

    /**
     * Parse, execute and render the document.
     * 
     * @param targetSyntax the syntax to use to render the document
     * @param executionContextIsolated see {@link DocumentDisplayerParameters#isExecutionContextIsolated()}
     * @param transformationContextIsolated see {@link DocumentDisplayerParameters#isTransformationContextIsolated()}
     * @param transformationContextRestricted see
     *            {@link DocumentDisplayerParameters#isTransformationContextRestricted()}
     * @param translate get translated content of the document
     * @return the result of the document execution rendered in the passed syntax
     * @throws XWikiException when failing to display the document
     */
    private String display(Syntax targetSyntax, boolean executionContextIsolated, boolean transformationContextIsolated,
        boolean transformationContextRestricted, boolean translate) throws XWikiException
    {
        // Note: We are currently duplicating code from getRendered signature because some calling
        // code is expecting that the rendering will happen in the calling document's context and not in this
        // document's context. For example this is true for the Admin page, see
        // https://jira.xwiki.org/browse/XWIKI-4274 for more details.

        getProgress().startStep(this, "document.progress.render", "Render document [{}] in syntax [{}]",
            getDocumentReference(), targetSyntax);

        try {
            getProgress().pushLevelProgress(3, getDocumentReference());

            getProgress().startStep(getDocumentReference(), "document.progress.render.translatedcontent",
                "Get translated content");

            XWikiContext xcontext = getXWikiContext();

            XWikiDocument tdoc = translate ? getTranslatedDocument(xcontext) : this;
            String translatedContent = tdoc.getContent();

            getProgress().startStep(getDocumentReference(), "document.progress.render.cache",
                "Try to get content from the cache");

            String renderedContent = getRenderingCache().getRenderedContent(tdoc.getDocumentReferenceWithLocale(),
                translatedContent, xcontext);

            if (renderedContent == null) {
                getProgress().startStep(getDocumentReference(), "document.progress.render.execute", "Execute content");

                // Configure display
                DocumentDisplayerParameters parameters = new DocumentDisplayerParameters();
                parameters.setExecutionContextIsolated(executionContextIsolated);
                parameters.setTransformationContextIsolated(transformationContextIsolated);
                // Don't consider isRestricted() here as this could invoke a sheet.
                parameters.setTransformationContextRestricted(transformationContextRestricted);
                // Render the translated content (matching the current language) using this document's syntax.
                parameters.setContentTranslated(tdoc != this);
                parameters.setTargetSyntax(targetSyntax);

                // Execute display
                XDOM contentXDOM = getDocumentDisplayer().display(this, parameters);

                // Render the result
                renderedContent = renderXDOM(contentXDOM, targetSyntax);

                getRenderingCache().setRenderedContent(getDocumentReference(), translatedContent, renderedContent,
                    xcontext);
            }

            return renderedContent;
        } finally {
            getProgress().popLevelProgress(getDocumentReference());
            getProgress().endStep(this);
        }
    }

    public String getRenderedContent(Syntax targetSyntax, XWikiContext context) throws XWikiException
    {
        return getRenderedContent(targetSyntax, true, context);
    }

    /**
     * @since 8.4RC1
     */
    public String getRenderedContent(boolean transformationContextIsolated, XWikiContext context) throws XWikiException
    {
        return getRenderedContent(getOutputSyntax(), transformationContextIsolated, context);
    }

    /**
     * Execute and render the current document in the current context. The code is executed with right of this document
     * content author.
     *
     * @param context the XWiki Context object
     * @return the rendered content of the document or its translation.
     * @throws XWikiException in case of error during the rendering.
     * @since 11.3RC1
     */
    public String displayDocument(XWikiContext context) throws XWikiException
    {
        return displayDocument(getOutputSyntax(), context);
    }

    /**
     * Execute and render the current document in the current context. The code is executed with right of this document
     * content author.
     *
     * @param context the XWiki Context object
     * @param restricted see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @return the rendered content of the document or its translation.
     * @throws XWikiException in case of error during the rendering.
     * @since 11.5RC1
     */
    public String displayDocument(boolean restricted, XWikiContext context) throws XWikiException
    {
        return displayDocument(getOutputSyntax(), restricted, context);
    }

    /**
     * Execute and render the current document in the current context. The code is executed with right of this document
     * content author.
     *
     * @param targetSyntax the syntax to use to render the document
     * @param context the XWiki Context object
     * @return the rendered content of the document or its translation.
     * @throws XWikiException in case of error during the rendering.
     * @since 11.3RC1
     */
    public String displayDocument(Syntax targetSyntax, XWikiContext context) throws XWikiException
    {
        return getRenderedContent(targetSyntax, true, false, context, false);
    }

    /**
     * Execute and render the current document in the current context. The code is executed with right of this document
     * content author.
     *
     * @param targetSyntax the syntax to use to render the document
     * @param context the XWiki Context object
     * @param restricted see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @return the rendered content of the document or its translation.
     * @throws XWikiException in case of error during the rendering.
     * @since 11.5RC1
     */
    public String displayDocument(Syntax targetSyntax, boolean restricted, XWikiContext context) throws XWikiException
    {
        return getRenderedContent(targetSyntax, true, restricted, context, false);
    }

    /**
     * Execute and render the document or its translation in the current context. The code is executed with right of
     * this document (or the translation) content author. The translations are retrieved if they exist and based on
     * XWiki preferences (see {@link #getTranslatedDocument(XWikiContext)}).
     *
     * @param targetSyntax the syntax to use to render the document
     * @param transformationContextIsolated see {@link DocumentDisplayerParameters#isTransformationContextIsolated()}
     * @param context the XWiki Context object
     * @return the rendered content of the document or its translation.
     * @throws XWikiException in case of error during the rendering.
     */
    public String getRenderedContent(Syntax targetSyntax, boolean transformationContextIsolated, XWikiContext context)
        throws XWikiException
    {
        return getRenderedContent(targetSyntax, transformationContextIsolated, false, context, true);
    }

    /**
     * Execute and render the document or its translation in the current context. The code is executed with right of
     * this document (or the translation) content author.
     *
     * @param targetSyntax the syntax to use to render the document
     * @param transformationContextIsolated see {@link DocumentDisplayerParameters#isTransformationContextIsolated()}
     * @param transformationContextRestricted see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @param context the XWiki Context object
     * @param retrieveTranslation if true retrieve the translation of the document according to the preferences (see
     *            {@link #getTranslatedDocument(XWikiContext)}). If false, render the current document.
     * @return the rendered content of the document or its translation.
     * @throws XWikiException in case of error during the rendering.
     */
    private String getRenderedContent(Syntax targetSyntax, boolean transformationContextIsolated,
        boolean transformationContextRestricted, XWikiContext context, boolean retrieveTranslation)
        throws XWikiException
    {
        // Make sure the context secure document is the current document so that it's executed with its own
        // rights
        Object currentSdoc = context.get("sdoc");
        try {
            XWikiDocument sdoc;

            if (retrieveTranslation) {
                sdoc = getTranslatedDocument(context);
            } else {
                sdoc = this;
            }
            context.put("sdoc", sdoc);

            return display(targetSyntax, false, transformationContextIsolated, transformationContextRestricted,
                retrieveTranslation);
        } finally {
            context.put("sdoc", currentSdoc);
        }
    }

    public String getRenderedContent(XWikiContext context) throws XWikiException
    {
        return getRenderedContent(getOutputSyntax(), context);
    }

    /**
     * @param text the text to render
     * @param syntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param context the XWiki Context object
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 1.6M1
     */
    public String getRenderedContent(String text, String syntaxId, XWikiContext context)
    {
        return getRenderedContent(text, syntaxId, getOutputSyntax().toIdString(), context);
    }

    /**
     * @param text the text to render
     * @param syntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param restrictedTransformationContext see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @param context the XWiki Context object
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 4.2M1
     */
    public String getRenderedContent(String text, String syntaxId, boolean restrictedTransformationContext,
        XWikiContext context)
    {
        return getRenderedContent(text, syntaxId, getOutputSyntax().toIdString(), restrictedTransformationContext,
            context);
    }

    /**
     * @param text the text to render
     * @param syntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param restrictedTransformationContext see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @param sDocument the {@link XWikiDocument} to use as secure document, if null keep the current one
     * @param context the XWiki Context object
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 8.3
     */
    public String getRenderedContent(String text, String syntaxId, boolean restrictedTransformationContext,
        XWikiDocument sDocument, XWikiContext context)
    {
        return getRenderedContent(text, syntaxId, getOutputSyntax().toIdString(), restrictedTransformationContext,
            sDocument, context);
    }

    /**
     * @param text the text to render
     * @param sourceSyntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param targetSyntaxId the id of the syntax in which to render the document content
     * @param context the XWiki context
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 2.0M3
     */
    public String getRenderedContent(String text, String sourceSyntaxId, String targetSyntaxId, XWikiContext context)
    {
        return getRenderedContent(text, sourceSyntaxId, targetSyntaxId, false, context);
    }

    /**
     * @param text the text to render
     * @param sourceSyntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param targetSyntaxId the id of the syntax in which to render the document content
     * @param restrictedTransformationContext see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @param context the XWiki context
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 4.2M1
     */
    public String getRenderedContent(String text, String sourceSyntaxId, String targetSyntaxId,
        boolean restrictedTransformationContext, XWikiContext context)
    {
        return getRenderedContent(text, sourceSyntaxId, targetSyntaxId, restrictedTransformationContext, null, context);
    }

    /**
     * @param text the text to render
     * @param sourceSyntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param targetSyntaxId the id of the syntax in which to render the document content
     * @param restrictedTransformationContext see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @param sDocument the {@link XWikiDocument} to use as secure document, if null keep the current one
     * @param context the XWiki context
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 8.3
     */
    public String getRenderedContent(String text, String sourceSyntaxId, String targetSyntaxId,
        boolean restrictedTransformationContext, XWikiDocument sDocument, XWikiContext context)
    {
        try {
            return getRenderedContent(text, Syntax.valueOf(sourceSyntaxId), Syntax.valueOf(targetSyntaxId),
                restrictedTransformationContext, sDocument, true, context);
        } catch (ParseException e) {
            // Failed to render for some reason. This method should normally throw an exception but this
            // requires changing the signature of calling methods too.
            LOGGER.warn("Failed to render content [{}]", text, e);
        }

        return "";
    }

    /**
     * @param text the text to render
     * @param sourceSyntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param sDocument the {@link XWikiDocument} to use as secure document, if null keep the current one
     * @param isolated true of the content should be executed in this document's context
     * @param context the XWiki context
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 13.0
     */
    public String getRenderedContent(String text, Syntax sourceSyntaxId, XWikiDocument sDocument, boolean isolated,
        XWikiContext context)
    {
        return getRenderedContent(text, sourceSyntaxId, getOutputSyntax(), false, sDocument, isolated, context);
    }

    /**
     * @param text the text to render
     * @param sourceSyntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param restrictedTransformationContext see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @param sDocument the {@link XWikiDocument} to use as secure document, if null keep the current one
     * @param isolated true of the content should be executed in this document's context
     * @param context the XWiki context
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 14.10
     * @since 14.4.7
     * @since 13.10.11
     */
    public String getRenderedContent(String text, Syntax sourceSyntaxId, boolean restrictedTransformationContext,
        XWikiDocument sDocument, boolean isolated, XWikiContext context)
    {
        return getRenderedContent(text, sourceSyntaxId, getOutputSyntax(), restrictedTransformationContext, sDocument,
            isolated, context);
    }

    /**
     * @param text the text to render
     * @param sourceSyntaxId the id of the Syntax used by the passed text (e.g. {@code xwiki/2.1})
     * @param targetSyntaxId the id of the syntax in which to render the document content
     * @param restrictedTransformationContext see {@link DocumentDisplayerParameters#isTransformationContextRestricted}.
     * @param sDocument the {@link XWikiDocument} to use as secure document, if null keep the current one
     * @param isolated true of the content should be executed in this document's context
     * @param context the XWiki context
     * @return the given text rendered in the context of this document using the passed Syntax
     * @since 13.0
     */
    public String getRenderedContent(String text, Syntax sourceSyntaxId, Syntax targetSyntaxId,
        boolean restrictedTransformationContext, XWikiDocument sDocument, boolean isolated, XWikiContext context)
    {
        Map<String, Object> backup = null;

        getProgress().startStep(this, "document.progress.renderText",
            "Execute content [{}] in the context of document [{}]",
            StringUtils.substring(text, 0, 100) + (text.length() >= 100 ? "..." : ""), getDocumentReference());

        XWikiDocument currentSDocument = (XWikiDocument) context.get(CKEY_SDOC);
        try {
            // We have to render the given text in the context of this document. Check if this document is already
            // on the context (same Java object reference). We don't check if the document references are equal
            // because this document can have temporary changes that are not present on the context document even if
            // it has the same document reference.
            if (isolated && context.getDoc() != this) {
                backup = new HashMap<>();
                backupContext(backup, context);
                setAsContextDoc(context);
            }

            // Make sure to execute the document with the right of the provided sdocument's author
            if (sDocument != null) {
                context.put(CKEY_SDOC, sDocument);
            }

            // Reuse this document's reference so that the Velocity macro name-space is computed based on it.
            XWikiDocument fakeDocument = new XWikiDocument(getDocumentReference());
            fakeDocument.setSyntax(sourceSyntaxId);
            fakeDocument.setContent(text);
            fakeDocument.setRestricted(sDocument != null && sDocument.isRestricted());

            // We don't let displayer take care of the context isolation because we don't want the fake document to be
            // context document
            return fakeDocument.display(targetSyntaxId, false, isolated, restrictedTransformationContext, false);
        } catch (Exception e) {
            // Failed to render for some reason. This method should normally throw an exception but this
            // requires changing the signature of calling methods too.
            LOGGER.warn("Failed to render content [{}]", text, e);
        } finally {
            if (backup != null) {
                restoreContext(backup, context);
            }
            context.put(CKEY_SDOC, currentSDocument);

            getProgress().endStep(this);
        }

        return "";
    }

    public String getEscapedContent(XWikiContext context) throws XWikiException
    {
        return XMLUtils.escape(getTranslatedContent(context));
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    public String getName()
    {
        return getDocumentReference().getName();
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for loading a XWikiDocument.
     *
     * @deprecated use {@link #setDocumentReference(DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public void setName(String name)
    {
        if (name != null) {
            DocumentReference reference = getDocumentReference();
            // TODO: ensure that other parameters are copied properly
            setDocumentReferenceInternal(
                new DocumentReference(name, new SpaceReference(reference.getParent()), reference.getLocale()));
        }
    }

    @Override
    public DocumentReference getDocumentReference()
    {
        return this.documentReference;
    }

    /**
     * @return the reference of the document as {@link PageReference}
     * @since 10.6RC1
     */
    public PageReference getPageReference()
    {
        if (this.pageReferenceCache == null) {
            this.pageReferenceCache = intern(getPageReferenceResolver().resolve(getDocumentReference()));
        }

        return this.pageReferenceCache;
    }

    /**
     * @return the reference of the document as {@link PageReference} including the {@link Locale}
     * @since 10.6RC1
     */
    public PageReference getPageReferenceWithLocale()
    {
        if (this.pageReferenceWithLocaleCache == null) {
            this.pageReferenceWithLocaleCache = intern(new PageReference(getPageReference(), getLocale()));
        }

        return this.pageReferenceWithLocaleCache;
    }

    /**
     * @return the {@link DocumentReference} of the document also containing the document {@link Locale}
     * @since 5.3M2
     */
    public DocumentReference getDocumentReferenceWithLocale()
    {
        if (this.documentReferenceWithLocaleCache == null) {
            this.documentReferenceWithLocaleCache = intern(new DocumentReference(this.documentReference, getLocale()));
        }

        return this.documentReferenceWithLocaleCache;
    }

    /**
     * @return the document's space + page name (eg "space.page")
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    @Override
    public String getFullName()
    {
        return LOCAL_REFERENCE_SERIALIZER.serialize(getDocumentReference());
    }

    /**
     * @return the docoument's wiki + space + page name (eg "wiki:space.page")
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    public String getPrefixedFullName()
    {
        return getDefaultEntityReferenceSerializer().serialize(getDocumentReference());
    }

    /**
     * @since 2.2M1
     * @deprecated don't change the reference of a document once it's been constructed. Instead you can clone the doc,
     *             rename it or copy it.
     */
    @Deprecated(since = "2.2.3")
    public void setDocumentReference(DocumentReference reference)
    {
        // Don't allow setting a null reference for now, ie. don't do anything to preserve backward compatibility
        // with previous behavior (i.e. {@link #setFullName}.
        if (reference != null) {
            // Retro compatibility, make sure <code>this.documentReference</code> does not contain the Locale (for now)
            DocumentReference referenceWithoutLocale =
                reference.getLocale() != null ? new DocumentReference(reference, (Locale) null) : reference;

            if (!referenceWithoutLocale.equals(getDocumentReference())) {
                setDocumentReferenceInternal(referenceWithoutLocale);
            }
        }
    }

    private void setDocumentReferenceInternal(DocumentReference reference)
    {
        this.documentReference = intern(reference);

        setMetaDataDirty(true);

        // Clean various caches

        this.keyCache = null;
        this.localKeyCache = null;
        this.parentReferenceCache = null;
        this.documentReferenceWithLocaleCache = null;
        this.pageReferenceCache = null;
        this.pageReferenceWithLocaleCache = null;
    }

    /**
     * @deprecated use {@link #setDocumentReference(org.xwiki.model.reference.DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public void setFullName(String name)
    {
        setFullName(name, null);
    }

    /**
     * @deprecated use {@link #setDocumentReference(org.xwiki.model.reference.DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public void setFullName(String fullName, XWikiContext context)
    {
        // We ignore the passed full name if it's null to be backward compatible with previous behaviors and to be
        // consistent with {@link #setName} and {@link #setSpace}.
        if (fullName != null) {
            // Note: We use the CurrentMixed Resolver since we want to use the default page name if the page isn't
            // specified in the passed string, rather than use the current document's page name.
            setDocumentReference(getCurrentMixedDocumentReferenceResolver().resolve(fullName));
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see DocumentModelBridge#getWikiName()
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    @Override
    public String getWikiName()
    {
        return getDatabase();
    }

    /**
     * {@inheritDoc}
     *
     * @see DocumentModelBridge#getSpaceName()
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    @Override
    public String getSpaceName()
    {
        return this.getSpace();
    }

    /**
     * {@inheritDoc}
     *
     * @see DocumentModelBridge#getSpaceName()
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    @Override
    public String getPageName()
    {
        return this.getName();
    }

    @Override
    public String getTitle()
    {
        return (this.title != null) ? this.title : "";
    }

    @Override
    public Object getPreparedTitle()
    {
        // If the content is prepared and it's allowed to use the cache, return it
        if (this.titlePrepareDate != null) {
            if (getCacheControl().isCacheReadAllowed(this.titlePrepareDate)) {
                return this.preparedTitle;
            }
        }

        return null;
    }

    @Override
    public void setPreparedTitle(Object preparedTitle)
    {
        this.preparedTitle = preparedTitle;
        this.titlePrepareDate = LocalDateTime.now();
    }

    /**
     * Get the rendered version of the document title. The title is extracted and then Velocity is applied on it and
     * it's then rendered using the passed Syntax. The following logic is used to extract the title:
     * <ul>
     * <li>If a Sheet is specified for the document and this Sheet document contains a non empty title then it's
     * used</li>
     * <li>If not and the document's title is specified then it's used</li>
     * <li>If not and if the title compatibility mode is turned on ({@code xwiki.title.compatibility=1} in
     * {@code xwiki.cfg}) then an attempt is made to extract the title from the first heading found in the document's
     * content</li>
     * <li>If not, then at last resort the page name is returned</li>
     * </ul>
     *
     * @param outputSyntax the syntax to render to; this is not taken into account for XWiki 1.0 syntax
     * @param context the XWiki context
     * @return the rendered version of the document title
     */
    public String getRenderedTitle(Syntax outputSyntax, XWikiContext context)
    {
        DocumentDisplayerParameters parameters = new DocumentDisplayerParameters();
        parameters.setTitleDisplayed(true);
        parameters.setExecutionContextIsolated(true);
        parameters.setTargetSyntax(outputSyntax);
        try {
            XDOM titleXDOM = getDocumentDisplayer().display(this, parameters);
            return renderXDOM(titleXDOM, outputSyntax);
        } catch (Exception e) {
            // We've failed to extract the Document's title or to render it. We log an error but we use the page name
            // as the returned title in order to not generate errors in lots of places in the wiki (e.g. Activity
            // Stream, menus, etc). The title is used in a lots of places...
            LOGGER.error("Failed to render title for [{}]", getDocumentReference(), e);
            return getDocumentReference().getName();
        }
    }

    /**
     * Similar to {@link #getRenderedTitle(Syntax, XWikiContext)} but the output Syntax used is XHTML 1.0 unless the
     * current skin defines another output Syntax in which case it's the one used.
     *
     * @param context the XWiki context
     * @return the rendered version of the document title
     */
    public String getRenderedTitle(XWikiContext context)
    {
        return getRenderedTitle(getOutputSyntax(), context);
    }

    public void setTitle(String title)
    {
        if (title != null && !title.equals(this.title)) {
            // Document titles usually contain velocity script, so it is not enough to set the metadata dirty, since we
            // want to content author to be updated for programming or script rights to be updated.
            setContentDirty(true);
        }
        this.title = title;
        this.titlePrepareDate = null;
        this.preparedTitle = null;
    }

    public String getFormat()
    {
        return this.format != null ? this.format : "";
    }

    public void setFormat(String format)
    {
        if (!format.equals(this.format)) {
            this.format = format;

            setMetaDataDirty(true);
        }
    }

    /**
     * @param userString the user {@link String} to convert to {@link DocumentReference}
     * @return the user as {@link DocumentReference}
     */
    private DocumentReference userStringToReference(String userString)
    {
        DocumentReference userReference;

        if (StringUtils.isEmpty(userString)) {
            userReference = null;
        } else {
            userReference = getExplicitReferenceDocumentReferenceResolver().resolve(
                getXClassEntityReferenceResolver().resolve(userString, EntityType.DOCUMENT), getDocumentReference());

            if (userReference.getName().equals(XWikiRightService.GUEST_USER)) {
                userReference = null;
            }
        }

        return userReference;
    }

    /**
     * @param userReference the user {@link DocumentReference} to convert to {@link String}
     * @return the user as String
     */
    private String userReferenceToString(DocumentReference userReference)
    {
        String userString;

        if (userReference != null) {
            userString = getCompactWikiEntityReferenceSerializer().serialize(userReference, getDocumentReference());
        } else {
            userString = XWikiRightService.GUEST_USER_FULLNAME;
        }

        return userString;
    }

    /**
     * @param userReference the user {@link DocumentReference} to convert to {@link String}
     * @return the user as String
     */
    private String userReferenceToString(UserReference userReference)
    {
        // The user API is missing the concept of relative user references ATM so we're forced to check where the users
        // are stored in order to make sure user references stored in the database are relative.
        // See also XWIKI-19442: APIs to generate various String references from a UserReference
        if ("document".equals(getUserConfiguration().getStoreHint())) {
            // Users are stored as documents. We want the user references that are stored in the database to be relative
            // as much as possible (because it makes the content portable). For this we omit the wiki reference when the
            // user (profile document) reference is from the same wiki as this document.
            return getUserReferenceCompactWikiSerializer().serialize(userReference, getDocumentReference());
        } else {
            return getUserReferenceStringSerializer().serialize(userReference);
        }
    }

    /**
     * @param userString the user {@link String} to convert to {@link UserReference}
     * @return the user as {@link UserReference}
     */
    private UserReference userStringToUserReference(String userString)
    {
        // The user API is missing the concept of relative user references ATM so if we want to resolve (partial) user
        // references that were stored in the database relative to this document then we need to check where the users
        // are stored. See also XWIKI-19442: APIs to generate various String references from a UserReference
        if ("document".equals(getUserConfiguration().getStoreHint())) {
            return getUserReferenceStringResolver().resolve(userString, getDocumentReference().getWikiReference());
        } else {
            return getUserReferenceStringResolver().resolve(userString);
        }
    }

    /**
     * @since 3.0M3
     * @deprecated use {@link #getAuthors()} and then {@link DocumentAuthors#getEffectiveMetadataAuthor()} instead
     */
    @Deprecated(since = "14.0RC1")
    public DocumentReference getAuthorReference()
    {
        UserReference effectiveMetadataAuthor = getAuthors().getEffectiveMetadataAuthor();
        if (this.getAuthors().getEffectiveMetadataAuthor() != null
            && effectiveMetadataAuthor != GuestUserReference.INSTANCE) {
            return this.getUserReferenceDocumentReferenceSerializer().serialize(effectiveMetadataAuthor);
        } else {
            return null;
        }
    }

    /**
     * @since 3.0M3
     * @deprecated use {@link #getAuthors()} and then {@link DocumentAuthors#setEffectiveMetadataAuthor(UserReference)}
     *             instead
     */
    @Deprecated(since = "14.0RC1")
    public void setAuthorReference(DocumentReference authorReference)
    {
        if (authorReference == null) {
            this.authors.setEffectiveMetadataAuthor(GuestUserReference.INSTANCE);
        } else {
            if (authorReference.getName().equals(XWikiRightService.GUEST_USER)) {
                LOGGER.warn("A reference to XWikiGuest user has been set instead of null. This is probably a mistake.",
                    new Exception("See stack trace"));
            }
            UserReference user = this.getUserReferenceDocumentReferenceResolver().resolve(authorReference);
            this.authors.setEffectiveMetadataAuthor(user);
            // We also set the original metadata author for backward compatibility.
            this.authors.setOriginalMetadataAuthor(user);
        }
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated since 3.0M3 use {@link #getAuthorReference()} instead
     */
    @Deprecated
    public String getAuthor()
    {
        return userReferenceToString(getAuthorReference());
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for loading a XWikiDocument.
     *
     * @deprecated use {@link #setAuthorReference} instead
     */
    @Deprecated(since = "3.0M3")
    public void setAuthor(String author)
    {
        setAuthorReference(userStringToReference(author));
    }

    /**
     * @since 3.0M3
     * @deprecated use {@link #getAuthors()} and then {@link DocumentAuthors#getContentAuthor()} instead
     */
    @Override
    @Deprecated(since = "14.0RC1")
    public DocumentReference getContentAuthorReference()
    {
        UserReference contentAuthor = this.getAuthors().getContentAuthor();
        if (contentAuthor != null && contentAuthor != GuestUserReference.INSTANCE) {
            return this.getUserReferenceDocumentReferenceSerializer().serialize(contentAuthor);
        } else {
            return null;
        }
    }

    /**
     * @since 3.0M3
     * @deprecated use {@link #getAuthors()} and then {@link DocumentAuthors#setContentAuthor(UserReference)} instead
     */
    @Deprecated(since = "14.0RC1")
    public void setContentAuthorReference(DocumentReference contentAuthorReference)
    {
        if (contentAuthorReference == null) {
            this.authors.setContentAuthor(GuestUserReference.INSTANCE);
        } else {
            if (contentAuthorReference.getName().equals(XWikiRightService.GUEST_USER)) {
                LOGGER.warn("A reference to XWikiGuest user has been set instead of null. This is probably a mistake.",
                    new Exception("See stack trace"));
            }
            UserReference user = this.getUserReferenceDocumentReferenceResolver().resolve(contentAuthorReference);
            this.authors.setContentAuthor(user);
        }
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated use {@link #getContentAuthorReference()} instead
     */
    @Deprecated(since = "3.0M3")
    public String getContentAuthor()
    {
        return userReferenceToString(getContentAuthorReference());
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for loading a XWikiDocument.
     *
     * @deprecated use {@link #setContentAuthorReference} instead
     */
    @Deprecated(since = "3.0M3")
    public void setContentAuthor(String contentAuthor)
    {
        setContentAuthorReference(userStringToReference(contentAuthor));
    }

    /**
     * @since 3.0M3
     * @deprecated use {@link #getAuthors()} and then {@link DocumentAuthors#getCreator()} instead
     */
    @Deprecated(since = "14.0RC1")
    public DocumentReference getCreatorReference()
    {
        UserReference creator = this.getAuthors().getCreator();
        if (creator != null && creator != GuestUserReference.INSTANCE) {
            return this.getUserReferenceDocumentReferenceSerializer().serialize(creator);
        } else {
            return null;
        }
    }

    /**
     * @since 3.0M3
     * @deprecated use {@link #getAuthors()} and then {@link DocumentAuthors#setCreator(UserReference)} instead
     */
    @Deprecated(since = "14.0RC1")
    public void setCreatorReference(DocumentReference creatorReference)
    {
        if (creatorReference == null) {
            this.authors.setCreator(GuestUserReference.INSTANCE);
        } else {
            if (creatorReference.getName().equals(XWikiRightService.GUEST_USER)) {
                LOGGER.warn("A reference to XWikiGuest user has been set instead of null. This is probably a mistake.",
                    new Exception("See stack trace"));
            }
            UserReference user = this.getUserReferenceDocumentReferenceResolver().resolve(creatorReference);
            this.authors.setCreator(user);
        }
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated use {@link #getCreatorReference()} instead
     */
    @Deprecated(since = "3.0M2")
    public String getCreator()
    {
        return userReferenceToString(getCreatorReference());
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for loading a XWikiDocument.
     *
     * @deprecated use {@link #setCreatorReference} instead
     */
    @Deprecated(since = "3.0M2")
    public void setCreator(String creator)
    {
        setCreatorReference(userStringToReference(creator));
    }

    @Override
    public Date getDate()
    {
        if (this.updateDate == null) {
            return new Date();
        } else {
            return this.updateDate;
        }
    }

    public void setDate(Date date)
    {
        if ((date != null) && (!date.equals(this.updateDate))) {
            setMetaDataDirty(true);
        }
        // Make sure we drop milliseconds for consistency with the database
        if (date != null) {
            date.setTime((date.getTime() / 1000) * 1000);
        }
        this.updateDate = date;
    }

    @Override
    public Date getCreationDate()
    {
        if (this.creationDate == null) {
            return new Date();
        } else {
            return this.creationDate;
        }
    }

    public void setCreationDate(Date date)
    {
        if ((date != null) && (!date.equals(this.creationDate))) {
            setMetaDataDirty(true);
        }

        // Make sure we drop milliseconds for consistency with the database
        if (date != null) {
            date.setTime((date.getTime() / 1000) * 1000);
        }
        this.creationDate = date;
    }

    public Date getContentUpdateDate()
    {
        if (this.contentUpdateDate == null) {
            return new Date();
        } else {
            return this.contentUpdateDate;
        }
    }

    public void setContentUpdateDate(Date date)
    {
        if ((date != null) && (!date.equals(this.contentUpdateDate))) {
            setMetaDataDirty(true);
        }

        // Make sure we drop milliseconds for consistency with the database
        if (date != null) {
            date.setTime((date.getTime() / 1000) * 1000);
        }
        this.contentUpdateDate = date;
    }

    public String getMeta()
    {
        return this.meta;
    }

    public void setMeta(String meta)
    {
        if (meta == null) {
            if (this.meta != null) {
                setMetaDataDirty(true);
            }
        } else if (!meta.equals(this.meta)) {
            setMetaDataDirty(true);
        }
        this.meta = meta;
    }

    public void appendMeta(String meta)
    {
        StringBuilder buf = new StringBuilder(this.meta);
        buf.append(meta);
        buf.append("\n");
        this.meta = buf.toString();
        setMetaDataDirty(true);
    }

    public boolean isContentDirty()
    {
        return this.isContentDirty;
    }

    /**
     * Increment the current document version. This method will use {@link #getNextVersion(Version, boolean)} to compute
     * the new version.
     */
    public void incrementVersion()
    {
        this.version = getNextVersion(this.version, isMinorEdit());
    }

    /**
     * This method computes the next version and returns it, but won't change the current version. In order to change
     * the current version, see {@link #incrementVersion()}.
     *
     * @param version the based version from which to compute the next one.
     * @param minorEdit true means it's a minor edition.
     * @return the new version computed based on the current one.
     * @since 11.2RC1
     */
    public static Version getNextVersion(Version version, boolean minorEdit)
    {
        if (version == null) {
            return new Version("1.1");
        }
        if (minorEdit) {
            return version.next();
        } else {
            return version.getBranchPoint().next().newBranch(1);
        }
    }

    public void setContentDirty(boolean contentDirty)
    {
        this.isContentDirty = contentDirty;
    }

    public boolean isMetaDataDirty()
    {
        return this.isMetaDataDirty;
    }

    public void setMetaDataDirty(boolean metaDataDirty)
    {
        if (metaDataDirty && !this.isMetaDataDirty && isCached()) {
            // Warn about abusive modification of cached document
            LoggerConfiguration loggerConfiguration = Utils.getComponent(LoggerConfiguration.class);
            String logMessage = "Abusive modification of the cached document [{}]";
            IllegalStateException exception = new IllegalStateException("Abusive modification of the cached document");
            if (loggerConfiguration.isDeprecatedLogEnabled()) {
                // We generally don't print a stack trace in case of warning log, but in this specific case the warning
                // is almost useless without a way to know what code is responsible for this call
                LOGGER.warn(logMessage, getDocumentReferenceWithLocale(), exception);
            } else {
                LOGGER.debug(logMessage, getDocumentReferenceWithLocale(), exception);
            }
        }

        this.isMetaDataDirty = metaDataDirty;
    }

    /**
     * @param dirty true the value of the dirty flag(s)
     * @param deep true if the dirty flag should be set to all children
     * @since 17.2.1
     * @since 17.3.0RC1
     */
    @Unstable
    public void setDirty(boolean dirty, boolean deep)
    {
        setMetaDataDirty(dirty);
        setContentDirty(dirty);

        if (deep) {
            if (this.xClass != null) {
                this.xClass.setDirty(dirty, deep);
            }

            if (this.xObjects != null) {
                this.xObjects.values().forEach(objects -> objects.setDirty(dirty, deep));
            }

            this.attachmentList.setDirty(dirty, deep);
        }
    }

    /**
     * Indicate if flags indicating which part of the document has been modified can be trusted.
     * 
     * @return the true if change made to this {@link XWikiDocument} instance are tracked
     * @since 17.1.0RC1
     * @since 16.10.4
     * @since 16.4.7
     */
    @Unstable
    public boolean isChangeTracked()
    {
        return this.changeTracked;
    }

    /**
     * Indicate if flags indicating which part of the document has been modified can be trusted.
     * 
     * @param changeTracked true if change made to this {@link XWikiDocument} instance are tracked
     * @since 17.1.0RC1
     * @since 16.10.4
     * @since 16.4.7
     */
    @Unstable
    public void setChangeTracked(boolean changeTracked)
    {
        this.changeTracked = changeTracked;
    }

    public String getAttachmentURL(String filename, XWikiContext context)
    {
        return getAttachmentURL(filename, "download", context);
    }

    public String getAttachmentURL(String filename, String action, XWikiContext context)
    {
        return getAttachmentURL(filename, action, null, context);
    }

    public String getExternalAttachmentURL(String filename, String action, XWikiContext context)
    {
        URL url = context.getURLFactory().createAttachmentURL(filename, getSpace(), getName(), action, null,
            getDatabase(), context);
        return url.toString();
    }

    public String getAttachmentURL(String filename, String action, String querystring, XWikiContext context)
    {
        // Attachment file name cannot be empty
        if (StringUtils.isEmpty(filename)) {
            return null;
        }

        return context.getWiki().getAttachmentURL(new AttachmentReference(filename, this.getDocumentReference()),
            action, querystring, context);
    }

    public String getAttachmentRevisionURL(String filename, String revision, XWikiContext context)
    {
        return getAttachmentRevisionURL(filename, revision, null, context);
    }

    public String getAttachmentRevisionURL(String filename, String revision, String querystring, XWikiContext context)
    {
        // Attachment file name cannot be empty
        if (StringUtils.isEmpty(filename)) {
            return null;
        }

        return context.getWiki().getAttachmentRevisionURL(new AttachmentReference(filename, getDocumentReference()),
            revision, querystring, context);
    }

    /**
     * @param action the action, see the {@code struts-config.xml} file for a list of all existing action names
     * @param params the URL query string
     * @param redirect true if the URL is going to be used in {@link HttpServletResponse#sendRedirect(String)}
     * @param context the XWiki context
     * @return the URL
     */
    public String getURL(String action, String params, boolean redirect, XWikiContext context)
    {
        URL url =
            context.getURLFactory().createURL(getSpace(), getName(), action, params, null, getDatabase(), context);

        if (redirect && isRedirectAbsolute(context)) {
            if (url == null) {
                return null;
            } else {
                return url.toString();
            }
        } else {
            return context.getURLFactory().getURL(url, context);
        }
    }

    private boolean isRedirectAbsolute(XWikiContext context)
    {
        return Strings.CS.equals("1", context.getWiki().Param("xwiki.redirect.absoluteurl"));
    }

    public String getURL(String action, boolean redirect, XWikiContext context)
    {
        return getURL(action, null, redirect, context);
    }

    public String getURL(String action, XWikiContext context)
    {
        return getURL(action, false, context);
    }

    public String getURL(String action, String querystring, XWikiContext context)
    {
        URL url =
            context.getURLFactory().createURL(getSpace(), getName(), action, querystring, null, getDatabase(), context);
        return context.getURLFactory().getURL(url, context);
    }

    public String getURL(String action, String querystring, String anchor, XWikiContext context)
    {
        URL url = context.getURLFactory().createURL(getSpace(), getName(), action, querystring, anchor, getDatabase(),
            context);
        return context.getURLFactory().getURL(url, context);
    }

    public String getExternalURL(String action, XWikiContext context)
    {
        URL url = context.getURLFactory().createExternalURL(getSpace(), getName(), action, null, null, getDatabase(),
            context);
        return url.toString();
    }

    public String getExternalURL(String action, String querystring, XWikiContext context)
    {
        URL url = context.getURLFactory().createExternalURL(getSpace(), getName(), action, querystring, null,
            getDatabase(), context);
        return url.toString();
    }

    public String getParentURL(XWikiContext context) throws XWikiException
    {
        XWikiDocument doc = new XWikiDocument(getParentReference());
        URL url = context.getURLFactory().createURL(doc.getSpace(), doc.getName(), "view", null, null, getDatabase(),
            context);
        return context.getURLFactory().getURL(url, context);
    }

    public XWikiDocumentArchive getDocumentArchive(XWikiContext context) throws XWikiException
    {
        loadArchive(context);
        return getDocumentArchive();
    }

    /**
     * Create a new protected {@link com.xpn.xwiki.api.Document} public API to access page information and actions from
     * scripting.
     *
     * @param customClassName the name of the custom {@link com.xpn.xwiki.api.Document} class of the object to create.
     * @param context the XWiki context.
     * @return a wrapped version of an XWikiDocument. Prefer this function instead of new Document(XWikiDocument,
     *         XWikiContext)
     */
    public com.xpn.xwiki.api.Document newDocument(String customClassName, XWikiContext context)
    {
        if (!((customClassName == null) || (customClassName.equals("")))) {
            try {
                return newDocument(Class.forName(customClassName), context);
            } catch (ClassNotFoundException e) {
                LOGGER.error("Failed to get java Class object from class name", e);
            }
        }

        return new com.xpn.xwiki.api.Document(this, context);
    }

    /**
     * Create a new protected {@link com.xpn.xwiki.api.Document} public API to access page information and actions from
     * scripting.
     *
     * @param customClass the custom {@link com.xpn.xwiki.api.Document} class the object to create.
     * @param context the XWiki context.
     * @return a wrapped version of an XWikiDocument. Prefer this function instead of new Document(XWikiDocument,
     *         XWikiContext)
     */
    public com.xpn.xwiki.api.Document newDocument(Class<?> customClass, XWikiContext context)
    {
        if (customClass != null) {
            try {
                Class<?>[] classes = new Class[] {XWikiDocument.class, XWikiContext.class};
                Object[] args = new Object[] {this, context};

                return (com.xpn.xwiki.api.Document) customClass.getConstructor(classes).newInstance(args);
            } catch (Exception e) {
                LOGGER.error("Failed to create a custom Document object", e);
            }
        }

        return new com.xpn.xwiki.api.Document(this, context);
    }

    public com.xpn.xwiki.api.Document newDocument(XWikiContext context)
    {
        String customClass = getCustomClass();
        return newDocument(customClass, context);
    }

    public void loadArchive(XWikiContext context) throws XWikiException
    {
        if ((this.archive == null || this.archive.get() == null)) {
            XWikiDocumentArchive arch;
            // A document not coming from the database cannot have an archive stored in the database
            if (this.isNew()) {
                arch = new XWikiDocumentArchive(getDocumentReference().getWikiReference(), getId());
            } else {
                arch = getVersioningStore(context).getXWikiDocumentArchive(this, context);
            }
            // We are using a SoftReference which will allow the archive to be
            // discarded by the Garbage collector as long as the context is closed (usually during
            // the request)
            this.archive = new SoftReference<>(arch);
        }
    }

    /**
     * @return the {@link XWikiDocumentArchive} for this document. If it is not stored in the document, null is
     *         returned.
     */
    public XWikiDocumentArchive getDocumentArchive()
    {
        // If there is a soft reference, return it.
        if (this.archive != null) {
            return this.archive.get();
        }
        // Some APIs are expecting the archive to be null for loading it
        // (e.g. VersioningStore#loadXWikiDocumentArchive), so it's better to keep it null than to return an
        // empty archive which would never be populated.
        return null;
    }

    /**
     * @return the {@link XWikiDocumentArchive} for this document. If it is not stored in the document, we get it using
     *         the current context. If there is an exception, null is returned.
     */
    public XWikiDocumentArchive loadDocumentArchive()
    {
        XWikiDocumentArchive arch = getDocumentArchive();
        if (arch != null) {
            return arch;
        }

        // A document not coming from the database cannot have an archive stored in the database
        if (this.isNew()) {
            arch = new XWikiDocumentArchive(getDocumentReference().getWikiReference(), getId());
            setDocumentArchive(arch);
            return arch;
        }

        XWikiContext xcontext = getXWikiContext();

        try {
            arch = getVersioningStore(xcontext).getXWikiDocumentArchive(this, xcontext);

            // Put a copy of the archive in the soft reference for later use if needed.
            setDocumentArchive(arch);

            return arch;
        } catch (Exception e) {
            // VersioningStore.getXWikiDocumentArchive may throw an XWikiException, and xcontext or VersioningStore
            // may be null (tests)
            // To maintain the behavior of this method we can't throw an exception.
            // Formerly, null was returned if there was no SoftReference.
            LOGGER.warn("Could not get document archive", e);
            return null;
        }
    }

    public void setDocumentArchive(XWikiDocumentArchive arch)
    {
        // We are using a SoftReference which will allow the archive to be
        // discarded by the Garbage collector as long as the context is closed (usually during the
        // request)
        if (arch != null) {
            this.archive = new SoftReference<XWikiDocumentArchive>(arch);
        } else {
            // Some APIs are expecting the archive to be null for loading it
            // (e.g. VersioningStore#loadXWikiDocumentArchive), so we allow setting it back to null.
            this.archive = null;
        }
    }

    public void setDocumentArchive(String sarch) throws XWikiException
    {
        XWikiDocumentArchive xda = new XWikiDocumentArchive(getDocumentReference().getWikiReference(), getId());
        xda.setArchive(sarch);
        setDocumentArchive(xda);
    }

    public Version[] getRevisions(XWikiContext context) throws XWikiException
    {
        return getVersioningStore(context).getXWikiDocVersions(this, context);
    }

    /**
     * Counts the number of document versions matching criteria like author, minimum creation date, etc.
     *
     * @param criteria criteria used to match versions
     * @param context the XWiki context
     * @return the number of matching versions
     * @since 15.10.8
     * @since 16.2.0RC1
     */
    @Unstable
    public long getRevisionsCount(RevisionCriteria criteria, XWikiContext context) throws XWikiException
    {
        return getVersioningStore(context).getXWikiDocVersionsCount(this, criteria, context);
    }

    public String[] getRecentRevisions(int nb, XWikiContext context) throws XWikiException
    {
        try {
            Version[] revisions = getVersioningStore(context).getXWikiDocVersions(this, context);
            int length = nb;
            // 0 means all revisions
            if (nb == 0) {
                length = revisions.length;
            }

            if (revisions.length < length) {
                length = revisions.length;
            }

            String[] recentrevs = new String[length];
            for (int i = 1; i <= length; i++) {
                recentrevs[i - 1] = revisions[revisions.length - i].toString();
            }
            return recentrevs;
        } catch (Exception e) {
            return new String[0];
        }
    }

    /**
     * Gets document versions matching criteria like author, minimum creation date, etc.
     *
     * @param criteria criteria used to match versions
     * @return a list of matching versions
     */
    public List<String> getRevisions(RevisionCriteria criteria, XWikiContext context) throws XWikiException
    {
        // We collect explicitly into an ArrayList to ensure mutability of the resulting list.
        return getVersioningStore(context).getXWikiDocVersions(this, criteria, context).stream()
            .map(Version::toString)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public XWikiRCSNodeInfo getRevisionInfo(String version, XWikiContext context) throws XWikiException
    {
        return getDocumentArchive(context).getNode(new Version(version));
    }

    /**
     * @return Is this version the most recent one. False if and only if there are newer versions of this document in
     *         the database.
     */
    public boolean isMostRecent()
    {
        return this.mostRecent;
    }

    /**
     * must not be used unless in store system.
     *
     * @param mostRecent - mark document as most recent.
     */
    public void setMostRecent(boolean mostRecent)
    {
        this.mostRecent = mostRecent;
    }

    /**
     * @since 2.2M1
     */
    public BaseClass getXClass()
    {
        if (this.xClass == null) {
            BaseClass emptyClass = new BaseClass();
            // Make sure not to cause any false document versions if this document is saved.
            emptyClass.setDirty(false);

            this.setXClass(emptyClass);
        }
        return this.xClass;
    }

    /**
     * @since 2.2M1
     */
    public void setXClass(BaseClass xwikiClass)
    {
        xwikiClass.setOwnerDocument(this);

        this.xClass = xwikiClass;
    }

    /**
     * @since 2.2M1
     */
    public Map<DocumentReference, List<BaseObject>> getXObjects()
    {
        return this.publicXObjects;
    }

    /**
     * @since 2.2M1
     */
    public void setXObjects(Map<DocumentReference, List<BaseObject>> objects)
    {
        if (objects == null) {
            // Make sure we don`t set a null objects map since we assume everywhere that it is not null when using it.
            objects = new HashMap<>();
        }

        boolean isDirty = false;

        for (List<BaseObject> objList : objects.values()) {
            for (BaseObject obj : objList) {
                obj.setOwnerDocument(this);
                isDirty = true;
            }
        }

        // This operation resulted in marking the current document dirty.
        if (isDirty) {
            setMetaDataDirty(true);
        }

        // Replace the current objects with the provided ones.
        Map<DocumentReference, BaseObjects> objectsCopy = new ConcurrentSkipListMap<>();
        objects.forEach((k, v) -> objectsCopy.put(k, new BaseObjects(v)));
        this.xObjects = objectsCopy;
    }

    /**
     * @since 2.2M1
     */
    public BaseObject getXObject()
    {
        return getXObject(getDocumentReference());
    }

    /**
     * @deprecated use {@link #getXObject()} instead
     */
    @Deprecated(since = "2.2M1")
    public BaseObject getxWikiObject()
    {
        return getXObject(getDocumentReference());
    }

    /**
     * @since 2.2M1
     */
    public List<BaseClass> getXClasses(XWikiContext context)
    {
        List<BaseClass> list = new ArrayList<BaseClass>();

        // getXObjects() is a TreeMap, with elements sorted by className reference
        for (DocumentReference classReference : getXObjects().keySet()) {
            BaseClass bclass = null;
            List<BaseObject> objects = getXObjects(classReference);
            for (BaseObject obj : objects) {
                if (obj != null) {
                    bclass = obj.getXClass(context);
                    if (bclass != null) {
                        break;
                    }
                }
            }
            if (bclass != null) {
                list.add(bclass);
            }
        }
        return list;
    }

    /**
     * Create and add a new object to the document with the provided class.
     * <p>
     * Note that absolute reference are not supported for xclasses which mean that the wiki part (whatever the wiki is)
     * of the reference will be systematically removed.
     *
     * @param classReference the reference of the class
     * @param context the XWiki context
     * @return the index of teh newly created object
     * @throws XWikiException error when creating the new object
     * @since 2.2.3
     */
    public int createXObject(EntityReference classReference, XWikiContext context) throws XWikiException
    {
        DocumentReference absoluteClassReference = resolveClassReference(classReference);
        BaseObject object = BaseClass.newCustomClassInstance(absoluteClassReference, context);
        object.setOwnerDocument(this);
        object.setXClassReference(classReference);
        BaseObjects objects = this.xObjects.get(absoluteClassReference);
        if (objects == null) {
            objects = new BaseObjects();
            this.xObjects.put(absoluteClassReference, objects);
        }
        objects.add(object);
        int nb = objects.size() - 1;
        object.setNumber(nb);
        setMetaDataDirty(true);
        return nb;
    }

    /**
     * @deprecated use {@link #createXObject(EntityReference, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M1")
    public int createNewObject(String className, XWikiContext context) throws XWikiException
    {
        return createXObject(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()),
            context);
    }

    /**
     * @since 2.2M1
     */
    public int getXObjectSize(DocumentReference classReference)
    {
        try {
            return getXObjects().get(classReference).size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * @since 7.3M1
     * @since 7.2.1
     * @since 7.1.3
     * @since 6.4.6
     */
    public int getXObjectSize(EntityReference classReference)
    {
        return getXObjectSize(resolveClassReference(classReference));
    }

    /**
     * @deprecated use {@link #getXObjectSize(DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public int getObjectNumbers(String className)
    {
        return getXObjectSize(resolveClassReference(className));
    }

    /**
     * Retrieve and returns all objects corresponding to the given class reference, or an empty list if there's none. Be
     * aware that some elements of this list might be null since all objects are in the list at their real index.
     *
     * @param classReference the reference of the xclass for which to retrieve the xobjects
     * @return a list of xobjects and null elements (for deleted xobjects) corresponding to the given xclass or an empty
     *         list.
     * @since 2.2M1
     */
    public List<BaseObject> getXObjects(DocumentReference classReference)
    {
        List<BaseObject> xobjects = null;

        if (classReference != null) {
            xobjects = getXObjects().get(classReference);
        }

        return xobjects != null ? xobjects : Collections.emptyList();
    }

    /**
     * Retrieve and returns all objects corresponding to the class reference corresponding to the resolution of the
     * given entity reference, or an empty list if there's none. Be aware that some elements of this list might be null
     * since all objects are in the list at their real index.
     *
     * @param reference the reference that is resolved to an xclass for retrieving the corresponding xobjects
     * @return a list of xobjects and null elements (for deleted xobjects) corresponding to the given xclass or an empty
     *         list.
     * @since 3.3M1
     */
    public List<BaseObject> getXObjects(EntityReference reference)
    {
        if (reference.getType() == EntityType.DOCUMENT) {
            // class reference
            return getXObjects(
                getCurrentReferenceDocumentReferenceResolver().resolve(reference, getDocumentReference()));
        }

        return Collections.emptyList();
    }

    /**
     * @deprecated use {@link #getXObjects(DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public Vector<BaseObject> getObjects(String className)
    {
        List<BaseObject> result = this.xObjects.get(resolveClassReference(className));
        return result == null ? null : new Vector<BaseObject>(result);
    }

    /**
     * @since 2.2M1
     */
    public void setXObjects(DocumentReference classReference, List<BaseObject> objects)
    {
        // Remove existing objects
        List<BaseObject> existingbjects = this.xObjects.get(classReference);
        if (existingbjects != null) {
            existingbjects.clear();
        }

        for (BaseObject obj : objects) {
            obj.setOwnerDocument(this);
        }

        // Add new objects
        this.xObjects.put(classReference, new BaseObjects(objects));

        setMetaDataDirty(true);
    }

    /**
     * @since 3.3M1
     */
    public BaseObject getXObject(EntityReference reference)
    {
        if (reference instanceof DocumentReference) {
            return getXObject((DocumentReference) reference);
        } else if (reference.getType() == EntityType.DOCUMENT) {
            // class reference
            return getXObject(
                getCurrentReferenceDocumentReferenceResolver().resolve(reference, getDocumentReference()));
        } else if (reference.getType() == EntityType.OBJECT) {
            // object reference
            return getXObject(getCurrentReferenceObjectReferenceResolver().resolve(reference, getDocumentReference()));
        }

        return null;
    }

    /**
     * @since 2.2M1
     */
    public BaseObject getXObject(DocumentReference classReference)
    {
        BaseObject result = null;
        List<BaseObject> objects = getXObjects().get(classReference);
        if (objects != null) {
            for (BaseObject object : objects) {
                if (object != null) {
                    result = object;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Get an object of this document based on its reference.
     *
     * @param objectReference the reference of the object
     * @return the XWiki object
     * @since 3.2M1
     */
    public BaseObject getXObject(ObjectReference objectReference)
    {
        BaseObjectReference baseObjectReference = getBaseObjectReference(objectReference);

        // If the baseObjectReference has an object number, we return the object with this number,
        // otherwise, we consider it should be the first object, as specified by BaseObjectReference#getObjectNumber
        return baseObjectReference.getObjectNumber() == null ? this.getXObject(baseObjectReference.getXClassReference())
            : getXObject(baseObjectReference.getXClassReference(), baseObjectReference.getObjectNumber());
    }

    /**
     * Get or create an object of this document based on its reference.
     *
     * @param objectReference The reference of the object.
     * @param create If the object shall be created if missing.
     * @param context The XWiki context for creating the object.
     * @return The found or created objected.
     * @throws XWikiException If object creation failed.
     * @since 14.0RC1
     */
    public BaseObject getXObject(ObjectReference objectReference, boolean create, XWikiContext context)
        throws XWikiException
    {
        BaseObjectReference baseObjectReference = getBaseObjectReference(objectReference);

        // If the baseObjectReference has an object number, we return the object with this number,
        // otherwise, we consider it should be the first object, as specified by BaseObjectReference#getObjectNumber
        if (baseObjectReference.getObjectNumber() == null) {
            return getXObject(baseObjectReference.getXClassReference(), create, context);
        } else {
            return getXObject(baseObjectReference.getXClassReference(), baseObjectReference.getObjectNumber(), create,
                context);
        }
    }

    /**
     * Convert the given {@link ObjectReference} into a {@link BaseObjectReference}.
     */
    private BaseObjectReference getBaseObjectReference(ObjectReference objectReference)
    {
        if (objectReference instanceof BaseObjectReference) {
            return (BaseObjectReference) objectReference;
        } else {
            return new BaseObjectReference(objectReference);
        }
    }

    /**
     * Get an object property of this document based on its reference.
     *
     * @param objectPropertyReference the reference of the object property
     * @return the object property
     * @since 3.2M3
     */
    public BaseProperty<ObjectPropertyReference> getXObjectProperty(ObjectPropertyReference objectPropertyReference)
    {
        BaseObject object = getXObject((ObjectReference) objectPropertyReference.getParent());

        if (object != null) {
            return (BaseProperty<ObjectPropertyReference>) object.getField(objectPropertyReference.getName());
        }

        return null;
    }

    /**
     * @deprecated use {@link #getXObject(DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public BaseObject getObject(String className)
    {
        return getXObject(resolveClassReference(className));
    }

    /**
     * @since 2.2M1
     */
    public BaseObject getXObject(DocumentReference classReference, int nb)
    {
        List<BaseObject> objects = getXObjects().get(classReference);

        if (objects != null && objects.size() > nb) {
            return objects.get(nb);
        }

        return null;
    }

    /**
     * Get an xobject with the passed xclass at the passed location.
     * <p>
     * If <code>create</code> is true and the is no xobject at the passed located, it's created.
     *
     * @param classReference the xlcass of the object to retrieve
     * @param number the location of the xobject
     * @param create if true the xobject is created when it does not exist
     * @param xcontext the XWiki context
     * @return a {@link BaseObject} stored at passed location
     * @throws XWikiException when failing to create new xobject instance
     * @since 7.3M1
     * @since 7.2.1
     * @since 7.1.3
     * @since 6.4.6
     */
    public BaseObject getXObject(EntityReference classReference, int number, boolean create, XWikiContext xcontext)
        throws XWikiException
    {
        DocumentReference absoluteClassReference = resolveClassReference(classReference);

        BaseObject xobject = getXObject(absoluteClassReference, number);

        if (xobject == null && create) {
            xobject = BaseClass.newCustomClassInstance(absoluteClassReference, xcontext);

            setXObject(number, xobject);
        }

        return xobject;
    }

    /**
     * @since 4.1M1
     */
    public BaseObject getXObject(EntityReference classReference, int nb)
    {
        return getXObject(
            getCurrentReferenceDocumentReferenceResolver().resolve(classReference, getDocumentReference()), nb);
    }

    /**
     * @deprecated use {@link #getXObject(DocumentReference, int)} instead
     */
    @Deprecated(since = "2.2M1")
    public BaseObject getObject(String className, int nb)
    {
        return getXObject(resolveClassReference(className), nb);
    }

    /**
     * @since 2.2M1
     */
    public BaseObject getXObject(DocumentReference classReference, String key, String value)
    {
        return getXObject(classReference, key, value, false);
    }

    /**
     * @deprecated use {@link #getXObject(DocumentReference, String, String)} instead
     */
    @Deprecated(since = "2.2M1")
    public BaseObject getObject(String className, String key, String value)
    {
        return getObject(className, key, value, false);
    }

    /**
     * @return 6.3M1
     */
    public BaseObject getXObject(EntityReference reference, String key, String value, boolean failover)
    {
        if (reference instanceof DocumentReference) {
            return getXObject((DocumentReference) reference, key, value, failover);
        } else if (reference.getType() == EntityType.DOCUMENT) {
            // class reference
            return getXObject(getCurrentReferenceDocumentReferenceResolver().resolve(reference, getDocumentReference()),
                key, value, failover);
        }

        return null;
    }

    /**
     * @since 2.2M1
     */
    public BaseObject getXObject(DocumentReference classReference, String key, String value, boolean failover)
    {
        try {
            if (value == null) {
                if (failover) {
                    return getXObject(classReference);
                } else {
                    return null;
                }
            }

            List<BaseObject> objects = getXObjects().get(classReference);
            if ((objects == null) || (objects.size() == 0)) {
                return null;
            }
            for (BaseObject obj : objects) {
                if (obj != null) {
                    if (value.equals(obj.getStringValue(key))) {
                        return obj;
                    }
                }
            }

            if (failover) {
                return getXObject(classReference);
            } else {
                return null;
            }
        } catch (Exception e) {
            if (failover) {
                return getXObject(classReference);
            }

            LOGGER.warn("Exception while accessing objects for document [{}]: {}", getDocumentReference(),
                e.getMessage(), e);
            return null;
        }
    }

    /**
     * @deprecated use {@link #getXObject(DocumentReference, String, String, boolean)} instead
     */
    @Deprecated(since = "2.2M1")
    public BaseObject getObject(String className, String key, String value, boolean failover)
    {
        return getXObject(resolveClassReference(className), key, value, failover);
    }

    /**
     * @since 2.2M1
     * @deprecated use {@link #addXObject(BaseObject)} instead
     */
    @Deprecated
    public void addXObject(DocumentReference classReference, BaseObject object)
    {
        List<BaseObject> vobj = this.xObjects.get(classReference);
        if (vobj == null) {
            setXObject(classReference, 0, object);
        } else {
            setXObject(classReference, vobj.size(), object);
        }
    }

    /**
     * Add the object to the document.
     *
     * @param object the xobject to add
     * @throws NullPointerException if the specified object is null because we need the get the class reference from the
     *             object
     * @since 2.2.3
     */
    public void addXObject(BaseObject object)
    {
        object.setOwnerDocument(this);

        BaseObjects vobj = this.xObjects.get(object.getXClassReference());
        if (vobj == null) {
            setXObject(0, object);
        } else {
            setXObject(vobj.size(), object);
        }
    }

    /**
     * @deprecated use {@link #addXObject(BaseObject)} instead
     */
    @Deprecated(since = "2.2M1")
    public void addObject(String className, BaseObject object)
    {
        addXObject(resolveClassReference(className), object);
    }

    /**
     * @since 2.2M1
     * @deprecated use {@link #setXObject(int, BaseObject)} instead
     */
    @Deprecated
    public void setXObject(DocumentReference classReference, int nb, BaseObject object)
    {
        if (object != null) {
            object.setOwnerDocument(this);
            object.setNumber(nb);
        }

        BaseObjects objects = this.xObjects.computeIfAbsent(classReference, k -> new BaseObjects());
        objects.put(nb, object);

        setMetaDataDirty(true);
    }

    /**
     * Replaces the object at the specified position and for the specified object's xclass.
     *
     * @param nb index of the element to replace
     * @param object the xobject to insert
     * @throws NullPointerException if the specified object is null because we need the get the class reference from the
     *             object
     * @since 2.2.3
     */
    public void setXObject(int nb, BaseObject object)
    {
        object.setOwnerDocument(this);
        object.setNumber(nb);

        BaseObjects objects = this.xObjects.computeIfAbsent(object.getXClassReference(), k -> new BaseObjects());
        objects.put(nb, object);

        setMetaDataDirty(true);
    }

    /**
     * @deprecated use {@link #setXObject(DocumentReference, int, BaseObject)} instead
     */
    @Deprecated(since = "2.2M1")
    public void setObject(String className, int nb, BaseObject object)
    {
        setXObject(resolveClassReference(className), nb, object);
    }

    /**
     * @return true if the document is a new one (i.e. it has never been saved) or false otherwise
     */
    @Override
    public boolean isNew()
    {
        return this.isNew;
    }

    public void setNew(boolean aNew)
    {
        this.isNew = aNew;
    }

    /**
     * @since 2.2M1
     */
    public void mergeXClass(XWikiDocument templatedoc)
    {
        BaseClass bclass = getXClass();
        BaseClass tbclass = templatedoc.getXClass();
        if (tbclass != null) {
            if (bclass == null) {
                setXClass(tbclass.clone());
            } else {
                getXClass().merge(tbclass.clone());
            }
        }
        setMetaDataDirty(true);
    }

    /**
     * @deprecated use {@link #mergeXClass(XWikiDocument)} instead
     */
    @Deprecated(since = "2.2M1")
    public void mergexWikiClass(XWikiDocument templatedoc)
    {
        mergeXClass(templatedoc);
    }

    /**
     * @since 2.2M1
     */
    public void mergeXObjects(XWikiDocument templateDoc)
    {
        for (Map.Entry<DocumentReference, List<BaseObject>> entry : templateDoc.getXObjects().entrySet()) {
            // Documents can't have objects of types defined in a different wiki so we make sure the class reference
            // matches this document's wiki.
            DocumentReference classReference = entry.getKey().replaceParent(entry.getKey().getWikiReference(),
                getDocumentReference().getWikiReference());
            // Copy the objects from the template document only if this document doesn't have them already.
            //
            // Note: this might be a bit misleading since it will not add objects from the template if some objects of
            // that class already exist in the current document.
            if (getXObjectSize(classReference) == 0) {
                for (BaseObject object : entry.getValue()) {
                    if (object != null) {
                        addXObject(object.duplicate());
                    }
                }
            }
        }
    }

    /**
     * @deprecated use {@link #mergeXObjects(XWikiDocument)} instead
     */
    @Deprecated(since = "2.2M1")
    public void mergexWikiObjects(XWikiDocument templatedoc)
    {
        mergeXObjects(templatedoc);
    }

    /**
     * @since 2.2M1
     */
    public void cloneXObjects(XWikiDocument templatedoc)
    {
        cloneXObjects(templatedoc, true);
    }

    /**
     * @since 2.2.3
     */
    public void duplicateXObjects(XWikiDocument templatedoc)
    {
        cloneXObjects(templatedoc, false);
    }

    /**
     * Copy specified document objects into current document.
     *
     * @param templateDocument the document to copy
     * @param keepsIdentity if true it does an exact copy (same guid), otherwise it create a new object with the same
     *            values
     */
    private void cloneXObjects(XWikiDocument templateDocument, boolean keepsIdentity)
    {
        // clean map
        this.xObjects.clear();

        // fill map
        for (Map.Entry<DocumentReference, BaseObjects> entry : templateDocument.xObjects.entrySet()) {
            BaseObjects tobjects = entry.getValue();

            if (CollectionUtils.isNotEmpty(tobjects)) {
                BaseObjects objects = new BaseObjects(this, tobjects, keepsIdentity);

                if (!objects.isEmpty()) {
                    DocumentReference xclassReference = entry.getKey();
                    WikiReference wikiReference = getDocumentReference().getWikiReference();
                    if (!wikiReference.equals(xclassReference.getWikiReference())) {
                        // Make sure the class reference is in the same wiki as the document in which the object is
                        // stored
                        xclassReference = xclassReference.setWikiReference(wikiReference);
                    }

                    this.xObjects.put(xclassReference, objects);
                }
            }
        }
    }

    /**
     * @since 2.2M1
     */
    public DocumentReference getTemplateDocumentReference()
    {
        return this.templateDocumentReference;
    }

    /**
     * @deprecated use {@link #getTemplateDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    public String getTemplate()
    {
        String templateReferenceAsString;
        DocumentReference templateDocumentReference = getTemplateDocumentReference();
        if (templateDocumentReference != null) {
            templateReferenceAsString = LOCAL_REFERENCE_SERIALIZER.serialize(templateDocumentReference);
        } else {
            templateReferenceAsString = "";
        }
        return templateReferenceAsString;
    }

    /**
     * @since 2.2M1
     */
    public void setTemplateDocumentReference(DocumentReference templateDocumentReference)
    {
        if (!Objects.equals(getTemplateDocumentReference(), templateDocumentReference)) {
            this.templateDocumentReference = templateDocumentReference;
            setMetaDataDirty(true);
        }
    }

    /**
     * @deprecated use {@link #setTemplateDocumentReference(DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public void setTemplate(String template)
    {
        DocumentReference templateReference = null;
        if (!StringUtils.isEmpty(template)) {
            templateReference = getCurrentMixedDocumentReferenceResolver().resolve(template);
        }
        setTemplateDocumentReference(templateReference);
    }

    public String displayPrettyName(String fieldname, XWikiContext context)
    {
        return displayPrettyName(fieldname, false, true, context);
    }

    public String displayPrettyName(String fieldname, boolean showMandatory, XWikiContext context)
    {
        return displayPrettyName(fieldname, showMandatory, true, context);
    }

    public String displayPrettyName(String fieldname, boolean showMandatory, boolean before, XWikiContext context)
    {
        try {
            BaseObject object = getXObject();
            if (object == null) {
                object = getFirstObject(fieldname, context);
            }
            return displayPrettyName(fieldname, showMandatory, before, object, context);
        } catch (Exception e) {
            return "";
        }
    }

    public String displayPrettyName(String fieldname, BaseObject obj, XWikiContext context)
    {
        return displayPrettyName(fieldname, false, true, obj, context);
    }

    public String displayPrettyName(String fieldname, boolean showMandatory, BaseObject obj, XWikiContext context)
    {
        return displayPrettyName(fieldname, showMandatory, true, obj, context);
    }

    public String displayPrettyName(String fieldname, boolean showMandatory, boolean before, BaseObject obj,
        XWikiContext context)
    {
        try {
            PropertyClass pclass = (PropertyClass) obj.getXClass(context).get(fieldname);
            String dprettyName = "";
            if (showMandatory) {
                dprettyName = context.getWiki().addMandatory(context);
            }
            if (before) {
                return dprettyName + pclass.getPrettyName(context);
            } else {
                return pclass.getPrettyName(context) + dprettyName;
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * @param fieldname the name of the field to display
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, XWikiContext context)
    {
        String result = "";

        try {
            BaseObject object = getXObject();
            if (object == null) {
                object = getFirstObject(fieldname, context);
            }

            result = display(fieldname, object, context);
        } catch (Exception e) {
            LOGGER.error("Failed to display field [" + fieldname + "] of document ["
                + getDefaultEntityReferenceSerializer().serialize(getDocumentReference()) + "]", e);
        }

        return result;
    }

    /**
     * @param fieldname the name of the field to display
     * @param obj the object containing the field to display
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, BaseObject obj, XWikiContext context)
    {
        String type = null;
        try {
            type = (String) context.get("display");
        } catch (Exception e) {
        }

        if (type == null) {
            type = "view";
        }

        return display(fieldname, type, obj, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param mode the mode to use ("view", "edit", ...)
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, String mode, XWikiContext context)
    {
        return display(fieldname, mode, "", context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param obj the object containing the field to display
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, String type, BaseObject obj, XWikiContext context)
    {
        return display(fieldname, type, obj, true, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param obj the object containing the field to display
     * @param isolated true if the property should be displayed in it's own document context
     * @param context the XWiki context
     * @return the rendered field
     * @since 13.0
     */
    public String display(String fieldname, String type, BaseObject obj, boolean isolated, XWikiContext context)
    {
        return display(fieldname, type, "", obj, isolated, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param obj the object containing the field to display
     * @param isolated true if the property should be displayed in it's own document context
     * @param number true if the number you be part of the input name, false otherwise
     * @param context the XWiki context
     * @return the rendered field
     * @since 17.3.0RC1
     */
    @Unstable
    public String display(String fieldname, String type, BaseObject obj, boolean isolated, boolean number,
        XWikiContext context)
    {
        return display(fieldname, type, "", obj, isolated, number, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param mode the mode to use ("view", "edit", ...)
     * @param prefix the prefix to add in the field identifier in edit display for example
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, String mode, String prefix, XWikiContext context)
    {
        try {
            BaseObject object = getXObject();
            if (object == null) {
                object = getFirstObject(fieldname, context);
            }
            if (object == null) {
                return "";
            } else {
                return display(fieldname, mode, prefix, object, context);
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param obj the object containing the field to display
     * @param wrappingSyntaxId the syntax of the content in which the result will be included. This to take care of some
     *            escaping depending of the syntax.
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, String type, BaseObject obj, String wrappingSyntaxId, XWikiContext context)
    {
        return display(fieldname, type, "", obj, wrappingSyntaxId, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param pref the prefix to add in the field identifier in edit display for example
     * @param obj the object containing the field to display
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, String type, String pref, BaseObject obj, XWikiContext context)
    {
        return display(fieldname, type, pref, obj, true, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param pref the prefix to add in the field identifier in edit display for example
     * @param obj the object containing the field to display
     * @param isolated true if the property should be displayed in it's own document context
     * @param context the XWiki context
     * @return the rendered field
     * @since 13.0
     */
    public String display(String fieldname, String type, String pref, BaseObject obj, boolean isolated,
        XWikiContext context)
    {
        return display(fieldname, type, pref, obj, context.getWiki().getCurrentContentSyntaxId(getSyntaxId(), context),
            isolated, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param pref the prefix to add in the field identifier in edit display for example
     * @param obj the object containing the field to display
     * @param isolated true if the property should be displayed in it's own document context
     * @param number true if the number you be part of the input name, false otherwise
     * @param context the XWiki context
     * @return the rendered field
     * @since 17.3.0RC1
     */
    @Unstable
    public String display(String fieldname, String type, String pref, BaseObject obj, boolean isolated, boolean number,
        XWikiContext context)
    {
        return display(fieldname, type, pref, obj, context.getWiki().getCurrentContentSyntaxId(getSyntaxId(), context),
            isolated, number, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param pref the prefix to add in the field identifier in edit display for example
     * @param obj the object containing the field to display
     * @param wrappingSyntaxId the syntax of the content in which the result will be included. This to take care of some
     *            escaping depending of the syntax.
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, String type, String pref, BaseObject obj, String wrappingSyntaxId,
        XWikiContext context)
    {
        return display(fieldname, type, pref, obj, wrappingSyntaxId, true, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param pref the prefix to add in the field identifier in edit display for example
     * @param obj the object containing the field to display
     * @param wrappingSyntaxId the syntax of the content in which the result will be included. This to take care of some
     *            escaping depending of the syntax.
     * @param isolated true if the property should be displayed in it's own document context
     * @param context the XWiki context
     * @return the rendered field
     */
    public String display(String fieldname, String type, String pref, BaseObject obj, String wrappingSyntaxId,
        boolean isolated, XWikiContext context)
    {
        return display(fieldname, type, pref, obj, wrappingSyntaxId, isolated, true, context);
    }

    /**
     * @param fieldname the name of the field to display
     * @param type the type of the field to display
     * @param pref the prefix to add in the field identifier in edit display for example
     * @param obj the object containing the field to display
     * @param wrappingSyntaxId the syntax of the content in which the result will be included. This to take care of some
     *            escaping depending of the syntax.
     * @param isolated true if the property should be displayed in it's own document context
     * @param number true if the number you be part of the input name, false otherwise
     * @param context the XWiki context
     * @return the rendered field
     * @since 17.3.0RC1
     */
    @Unstable
    public String display(String fieldname, String type, String pref, BaseObject obj, String wrappingSyntaxId,
        boolean isolated, boolean number, XWikiContext context)
    {
        if (obj == null) {
            return "";
        }

        boolean isInRenderingEngine = BooleanUtils.toBoolean((Boolean) context.get("isInRenderingEngine"));
        HashMap<String, Object> backup = new HashMap<String, Object>();
        XWikiDocument currentSDoc = (XWikiDocument) context.get(CKEY_SDOC);
        try {
            if (isolated) {
                backupContext(backup, context);
                setAsContextDoc(context);
            }

            // Make sure to execute with the right of the document author instead of the content author
            // (because modifying object property does not modify content author)
            XWikiDocument sdoc = obj.getOwnerDocument();
            if (sdoc != null && !Objects.equals(sdoc.getContentAuthorReference(), sdoc.getAuthorReference())) {
                // Hack the sdoc to make test module believe the content author is the author
                sdoc = sdoc.clone();
                sdoc.setContentAuthorReference(sdoc.getAuthorReference());
                context.put(CKEY_SDOC, sdoc);
            }

            type = type.toLowerCase();
            StringBuffer result = new StringBuffer();
            PropertyClass pclass = (PropertyClass) obj.getXClass(context).get(fieldname);

            StringBuilder builder = new StringBuilder(pref);
            builder.append(LOCAL_REFERENCE_SERIALIZER.serialize(obj.getXClass(context).getDocumentReference()));
            if (number) {
                builder.append('_');
                builder.append(obj.getNumber());
            }
            builder.append('_');
            String prefix = builder.toString();

            if (pclass == null) {
                return "";
            } else if (pclass.isCustomDisplayed(context)) {
                pclass.displayCustom(result, fieldname, prefix, type, obj, context);
            } else if (type.equals("view")) {
                pclass.displayView(result, fieldname, prefix, obj, isolated, context);
            } else if (type.equals("rendered")) {
                String fcontent = pclass.displayView(fieldname, prefix, obj, context);
                // This mode is deprecated for the new rendering and should also be removed for the old rendering
                // since the way to implement this now is to choose the type of rendering to do in the class itself.
                // Thus for the new rendering we simply make this mode work like the "view" mode.
                if (is10Syntax(wrappingSyntaxId)) {
                    result.append(getRenderedContent(fcontent, getSyntaxId(), context));
                } else {
                    result.append(fcontent);
                }
            } else if (type.equals("edit")) {
                context.addDisplayedField(fieldname);
                // If the Syntax id is "xwiki/1.0" then use the old rendering subsystem and prevent wiki syntax
                // rendering using the pre macro. In the new rendering system it's the XWiki Class itself that does the
                // escaping. For example for a textarea check the TextAreaClass class.
                if (is10Syntax(wrappingSyntaxId)) {
                    // Don't use pre when not in the rendernig engine since for template we don't evaluate wiki syntax.
                    if (isInRenderingEngine) {
                        result.append("{pre}");
                    }
                }
                pclass.displayEdit(result, fieldname, prefix, obj, context);
                if (is10Syntax(wrappingSyntaxId)) {
                    if (isInRenderingEngine) {
                        result.append("{/pre}");
                    }
                }
            } else if (type.equals("hidden")) {
                // If the Syntax id is "xwiki/1.0" then use the old rendering subsystem and prevent wiki syntax
                // rendering using the pre macro. In the new rendering system it's the XWiki Class itself that does the
                // escaping. For example for a textarea check the TextAreaClass class.
                if (is10Syntax(wrappingSyntaxId) && isInRenderingEngine) {
                    result.append("{pre}");
                }
                pclass.displayHidden(result, fieldname, prefix, obj, context);
                if (is10Syntax(wrappingSyntaxId) && isInRenderingEngine) {
                    result.append("{/pre}");
                }
            } else if (type.equals("search")) {
                // Backward compatibility

                // Check if the method has been injected using aspects
                Method searchMethod = null;
                for (Method method : pclass.getClass().getMethods()) {
                    if (method.getName().equals("displaySearch") && method.getParameterTypes().length == 5) {
                        searchMethod = method;
                        break;
                    }
                }

                if (searchMethod != null) {
                    // If the Syntax id is "xwiki/1.0" then use the old rendering subsystem and prevent wiki syntax
                    // rendering using the pre macro. In the new rendering system it's the XWiki Class itself that does
                    // the
                    // escaping. For example for a textarea check the TextAreaClass class.
                    if (is10Syntax(wrappingSyntaxId) && isInRenderingEngine) {
                        result.append("{pre}");
                    }
                    prefix = LOCAL_REFERENCE_SERIALIZER.serialize(obj.getXClass(context).getDocumentReference()) + "_";
                    searchMethod.invoke(pclass, result, fieldname, prefix, context.get("query"), context);
                    if (is10Syntax(wrappingSyntaxId) && isInRenderingEngine) {
                        result.append("{/pre}");
                    }
                } else {
                    pclass.displayView(result, fieldname, prefix, obj, context);
                }
            } else {
                pclass.displayView(result, fieldname, prefix, obj, context);
            }

            // If we're in new rendering engine we want to wrap the HTML returned by displayView() in
            // a {{html/}} macro so that the user doesn't have to do it.
            // We test if we're inside the rendering engine since it's also possible that this display() method is
            // called directly from a template and in this case we only want HTML as a result and not wiki syntax.
            // TODO: find a more generic way to handle html macro because this works only for XWiki 1.0 and XWiki 2.0
            // Add the {{html}}{{/html}} only when result really contains html or { which could be part of an XWiki
            // macro syntax since it's not needed for pure text
            if (isInRenderingEngine && !is10Syntax(wrappingSyntaxId)
                && (HTMLUtils.containsElementText(result) || result.indexOf("{") != -1)) {
                // Escapes closing and opening HTML macro syntax. We need to escape the opening HTML macro syntax in
                // addition to the closing one as otherwise, the wrapping HTML macro might not close correctly.
                // For simplicity, to avoid having to deal with complex expressions that would need to be
                // synchronized with the parser, match just the start of the opening/closing macro syntax.
                return "{{html clean=\"false\" wiki=\"false\"}}"
                    + StringUtils.replaceEach(result.toString(), HTML_MACRO_SEARCH_STRINGS, HTML_MACRO_REPLACE_STRINGS)
                    + "{{/html}}";
            }

            return result.toString();
        } catch (Exception ex) {
            // TODO: It would better to check if the field exists rather than catching an exception
            // raised by a NPE as this is currently the case here...
            LOGGER.warn("Failed to display field [" + fieldname + "] in [" + type + "] mode for Object of Class ["
                + getDefaultEntityReferenceSerializer().serialize(obj.getDocumentReference()) + "]", ex);
            return "";
        } finally {
            if (!backup.isEmpty()) {
                restoreContext(backup, context);
            }
            context.put(CKEY_SDOC, currentSDoc);
        }
    }

    /**
     * @since 2.2M1
     */
    public String displayForm(DocumentReference classReference, String header, String format, XWikiContext context)
    {
        return displayForm(classReference, header, format, true, context);
    }

    /**
     * @deprecated use {@link #displayForm(DocumentReference, String, String, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M1")
    public String displayForm(String className, String header, String format, XWikiContext context)
    {
        return displayForm(className, header, format, true, context);
    }

    /**
     * @since 2.2M1
     */
    public String displayForm(DocumentReference classReference, String header, String format, boolean linebreak,
        XWikiContext context)
    {
        List<BaseObject> objects = getXObjects(classReference);
        if (format.endsWith("\\n")) {
            linebreak = true;
        }

        BaseObject firstobject = null;
        Iterator<BaseObject> foit = objects.iterator();
        while ((firstobject == null) && foit.hasNext()) {
            firstobject = foit.next();
        }

        if (firstobject == null) {
            return "";
        }

        BaseClass bclass = firstobject.getXClass(context);
        if (bclass.getPropertyList().size() == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        VelocityContext vcontext;
        try {
            vcontext = getVelocityContextFactory().createContext();
        } catch (XWikiVelocityException e) {
            LOGGER.error("Failed to create a standard VelocityContext", e);

            vcontext = new XWikiVelocityContext();
        }

        for (String propertyName : bclass.getPropertyList()) {
            PropertyClass pclass = (PropertyClass) bclass.getField(propertyName);
            vcontext.put(pclass.getName(), pclass.getPrettyName());
        }
        result.append(evaluate(header, context.getDoc().getPrefixedFullName(), vcontext, context));
        if (linebreak) {
            result.append("\n");
        }

        // display each line
        for (int i = 0; i < objects.size(); i++) {
            vcontext.put("id", Integer.valueOf(i + 1));
            BaseObject object = objects.get(i);
            if (object != null) {
                for (String name : bclass.getPropertyList()) {
                    vcontext.put(name, display(name, object, context));
                }
                result.append(evaluate(format, context.getDoc().getPrefixedFullName(), vcontext, context));
                if (linebreak) {
                    result.append("\n");
                }
            }
        }
        return result.toString();
    }

    private String evaluate(String content, String name, VelocityContext vcontext, XWikiContext context)
    {
        StringWriter writer = new StringWriter();
        try {
            VelocityManager velocityManager = Utils.getComponent(VelocityManager.class);
            velocityManager.getVelocityEngine().evaluate(vcontext, writer, name, content);
            return writer.toString();
        } catch (Exception e) {
            LOGGER.error("Error while parsing velocity template namespace [{}]", name, e);
            Object[] args = {name};
            XWikiException xe = new XWikiException(XWikiException.MODULE_XWIKI_RENDERING,
                XWikiException.ERROR_XWIKI_RENDERING_VELOCITY_EXCEPTION, "Error while parsing velocity page {0}", e,
                args);
            return Util.getHTMLExceptionMessage(xe, context);
        }
    }

    /**
     * @deprecated use {@link #displayForm(DocumentReference, String, String, boolean, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M1")
    public String displayForm(String className, String header, String format, boolean linebreak, XWikiContext context)
    {
        return displayForm(resolveClassReference(className), header, format, linebreak, context);
    }

    /**
     * @since 2.2M1
     */
    public String displayForm(DocumentReference classReference, XWikiContext context)
    {
        List<BaseObject> objects = getXObjects(classReference);
        if (objects == null) {
            return "";
        }

        BaseObject firstobject = null;
        Iterator<BaseObject> foit = objects.iterator();
        while ((firstobject == null) && foit.hasNext()) {
            firstobject = foit.next();
        }

        if (firstobject == null) {
            return "";
        }

        BaseClass bclass = firstobject.getXClass(context);
        if (bclass.getPropertyList().size() == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        result.append("{table}\n");
        boolean first = true;
        for (String propertyName : bclass.getPropertyList()) {
            if (first == true) {
                first = false;
            } else {
                result.append("|");
            }
            PropertyClass pclass = (PropertyClass) bclass.getField(propertyName);
            result.append(pclass.getPrettyName());
        }
        result.append("\n");
        for (int i = 0; i < objects.size(); i++) {
            BaseObject object = objects.get(i);
            if (object != null) {
                first = true;
                for (String propertyName : bclass.getPropertyList()) {
                    if (first == true) {
                        first = false;
                    } else {
                        result.append("|");
                    }
                    String data = display(propertyName, object, context);
                    data = data.trim();
                    data = data.replaceAll("\n", " ");
                    if (data.length() == 0) {
                        result.append("&nbsp;");
                    } else {
                        result.append(data);
                    }
                }
                result.append("\n");
            }
        }
        result.append("{table}\n");
        return result.toString();
    }

    /**
     * @deprecated use {@link #displayForm(DocumentReference, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M1")
    public String displayForm(String className, XWikiContext context)
    {
        return displayForm(resolveClassReference(className), context);
    }

    /**
     * Indicate if this {@link XWikiDocument} is currently in the document, implying that it's potentially shared with
     * several threads.
     * 
     * @return true if this {@link XWikiDocument} is in the document cache
     * @since 16.10.0RC1
     */
    @Unstable
    public boolean isCached()
    {
        return this.cached;
    }

    /**
     * @param cached true if this {@link XWikiDocument} is in the document cache
     * @since 16.10.0RC1
     */
    @Unstable
    public void setCached(boolean cached)
    {
        this.cached = cached;
    }

    /**
     * @deprecated use {@link #isCached()} instead
     */
    @Deprecated(since = "16.10.0RC1")
    public boolean isFromCache()
    {
        return this.fromCache;
    }

    /**
     * @deprecated use {@link #setCached(boolean)} instead
     */
    @Deprecated(since = "16.10.0RC1")
    public void setFromCache(boolean fromCache)
    {
        this.fromCache = fromCache;
    }

    public void readDocMetaFromForm(EditForm eform, XWikiContext context) throws XWikiException
    {
        String defaultLanguage = eform.getDefaultLanguage();
        if (defaultLanguage != null) {
            setDefaultLanguage(defaultLanguage);
        }

        String defaultTemplate = eform.getDefaultTemplate();
        if (defaultTemplate != null) {
            setDefaultTemplate(defaultTemplate);
        }

        String creator = eform.getCreator();
        if ((creator != null) && (!creator.equals(getCreator()))) {
            if ((getCreatorReference().equals(context.getUserReference()))
                || (context.getWiki().getRightService().hasAdminRights(context))) {
                setCreator(creator);
            }
        }

        String parent = eform.getParent();
        if (parent != null) {
            setParent(parent);
        }

        // Read the comment from the form
        String comment = eform.getComment();
        if (comment != null) {
            setComment(comment);
        }

        // Read the minor edit checkbox from the form
        setMinorEdit(eform.isMinorEdit());

        String tags = eform.getTags();
        if (!StringUtils.isEmpty(tags)) {
            setTags(tags, context);
        }

        // Set the Syntax id if defined
        String syntaxId = eform.getSyntaxId();
        if (syntaxId != null) {
            setSyntax(resolveSyntax(syntaxId));
        }

        // Read the hidden checkbox from the form
        if (eform.getHidden() != null) {
            setHidden("1".equals(eform.getHidden()));
        }

        // Read the enforce required rights flag from the form
        if (eform.getEnforceRequiredRights() != null) {
            setEnforceRequiredRights("1".equals(eform.getEnforceRequiredRights()));
        }
    }

    private Syntax resolveSyntax(String syntaxId)
    {
        Syntax syntax;
        try {
            syntax = getSyntaxRegistry().resolveSyntax(syntaxId);
        } catch (ParseException e) {
            syntax = getDefaultDocumentSyntax();
            LOGGER.warn("Failed to set syntax [{}] for [{}], setting syntax [{}] instead.", syntaxId,
                getDefaultEntityReferenceSerializer().serialize(getDocumentReference()), syntax.toIdString(), e);
        }
        return syntax;
    }

    /**
     * add tags to the document.
     */
    public void setTags(String tagsStr, XWikiContext context) throws XWikiException
    {
        BaseClass tagsClass = context.getWiki().getTagClass(context);

        StaticListClass tagProp = (StaticListClass) tagsClass.getField(XWikiConstant.TAG_CLASS_PROP_TAGS);

        BaseObject tags = getObject(XWikiConstant.TAG_CLASS, true, context);

        tags.safeput(XWikiConstant.TAG_CLASS_PROP_TAGS, tagProp.fromString(tagsStr));

        setMetaDataDirty(true);
    }

    public String getTags(XWikiContext context)
    {
        ListProperty prop = (ListProperty) getTagProperty();

        // I don't know why we need to XML-escape the list of tags but for backwards compatibility we need to keep doing
        // this. When this method was added it was using ListProperty#getTextValue() which used to return
        // ListProperty#toFormString() before we fixed it to return the unescaped value because we need to save the raw
        // value in the database and ListProperty#getTextValue() is called when the list property is saved.
        return prop != null ? prop.toFormString() : "";
    }

    public List<String> getTagsList(XWikiContext context)
    {
        List<String> tagList = List.of();

        BaseProperty prop = getTagProperty();
        if (prop != null) {
            tagList = (List<String>) prop.getValue();
        }

        return tagList;
    }

    private BaseProperty getTagProperty()
    {
        BaseObject tags = getObject(XWikiConstant.TAG_CLASS);

        return tags != null ? ((BaseProperty) tags.safeget(XWikiConstant.TAG_CLASS_PROP_TAGS)) : null;
    }

    public List<String> getTagsPossibleValues(XWikiContext context)
    {
        List<String> list;

        try {
            BaseClass tagsClass = context.getWiki().getTagClass(context);

            String possibleValues =
                ((StaticListClass) tagsClass.getField(XWikiConstant.TAG_CLASS_PROP_TAGS)).getValues();

            return ListClass.getListFromString(possibleValues);
        } catch (XWikiException e) {
            LOGGER.error("Failed to get tag class", e);

            list = Collections.emptyList();
        }

        return list;
    }

    public void readTranslationMetaFromForm(EditForm eform, XWikiContext context) throws XWikiException
    {
        String content = eform.getContent();
        if (content != null) {
            // Cleanup in case we use HTMLAREA
            // content = context.getUtil().substitute("s/<br class=\\\"htmlarea\\\"\\/>/\\r\\n/g",
            // content);
            content = context.getUtil().substitute("s/<br class=\"htmlarea\" \\/>/\r\n/g", content);
            setContent(content);
        }
        String title = eform.getTitle();
        if (title != null) {
            setTitle(title);
        }
    }

    /**
     * Updates properties of existing objects with the values from the given form.
     *
     * @param eform The form to read the values from
     * @param context The context used for getting the classes of objects
     * @throws XWikiException On errors
     */
    public void readObjectsFromForm(EditForm eform, XWikiContext context) throws XWikiException
    {
        for (DocumentReference reference : getXObjects().keySet()) {
            List<BaseObject> oldObjects = getXObjects(reference);
            BaseObjects newObjects = new BaseObjects();
            while (newObjects.size() < oldObjects.size()) {
                newObjects.add(null);
            }
            for (int i = 0; i < oldObjects.size(); i++) {
                BaseObject oldobject = oldObjects.get(i);
                if (oldobject != null) {
                    BaseClass baseclass = oldobject.getXClass(context);
                    BaseObject newobject = (BaseObject) baseclass.fromMap(
                        eform.getObject(
                            LOCAL_REFERENCE_SERIALIZER.serialize(baseclass.getDocumentReference()) + "_" + i),
                        oldobject);
                    newobject.setNumber(oldobject.getNumber());
                    newobject.setGuid(oldobject.getGuid());
                    newobject.setOwnerDocument(this);
                    newObjects.set(newobject.getNumber(), newobject);
                }
            }
            getXObjects().put(reference, newObjects);
        }
    }

    /**
     * Create and/or update objects in a document given a list of HTTP parameters of the form {@code
     * <spacename>.<classname>_<number>_<propertyname>}. If the object already exists, the field is replaced by the
     * given value. If the object doesn't exist in the document, it is created and the property {@code <propertyname>}
     * is initialized with the given value.
     *
     * @param eform is form information that contains all the query parameters
     * @param context
     * @throws XWikiException
     * @since 7.1M1
     */
    public void readObjectsFromFormUpdateOrCreate(EditForm eform, XWikiContext context) throws XWikiException
    {
        Map<String, SortedMap<Integer, Map<String, String[]>>> updateOrCreateMap = eform.getUpdateOrCreateMap();
        for (Entry<String, SortedMap<Integer, Map<String, String[]>>> requestClassEntries : updateOrCreateMap
            .entrySet()) {
            String className = requestClassEntries.getKey();
            DocumentReference requestClassReference = getCurrentDocumentReferenceResolver().resolve(className);

            SortedMap<Integer, Map<String, String[]>> requestObjectMap = requestClassEntries.getValue();
            for (Entry<Integer, Map<String, String[]>> requestObjectEntry : requestObjectMap.entrySet()) {
                Integer requestObjectNumber = requestObjectEntry.getKey();
                Map<String, String[]> requestObjectPropertyMap = requestObjectEntry.getValue();
                List<String> properties = new ArrayList<>(requestObjectPropertyMap.keySet());
                try {
                    BaseClass xClass = context.getWiki().getDocument(requestClassReference, context).getXClass();

                    // clean-up the properties that do not belong to the xclass
                    for (String property : properties) {
                        if (!xClass.getPropertyList().contains(property)) {
                            requestObjectPropertyMap.remove(property);
                        }
                    }
                } catch (XWikiException e) {
                    // If the class page cannot be found, skip entirely the property update
                    LOGGER.warn("Failed to load document [{}], ignoring properties update [{}]. Reason: [{}]",
                        requestClassReference, StringUtils.join(properties, ","),
                        ExceptionUtils.getRootCauseMessage(e));
                    continue;
                }

                if (!requestObjectPropertyMap.isEmpty()) {
                    BaseObject oldObject = getXObject(requestClassReference, requestObjectNumber, true, context);
                    BaseClass baseClass = oldObject.getXClass(context);
                    BaseObject newObject = (BaseObject) baseClass.fromMap(requestObjectPropertyMap, oldObject);
                    newObject.setNumber(oldObject.getNumber());
                    newObject.setGuid(oldObject.getGuid());
                    newObject.setOwnerDocument(this);
                    setXObject(requestObjectNumber, newObject);
                }
            }
        }
    }

    public void readFromForm(EditForm eform, XWikiContext context) throws XWikiException
    {
        readDocMetaFromForm(eform, context);
        readTranslationMetaFromForm(eform, context);

        readAddedUpdatedAndRemovedObjectsFromForm(eform, context);
        readTemporaryUploadedFiles(eform);
    }

    private TemporaryAttachmentSessionsManager getTemporaryAttachmentManager()
    {
        return Utils.getComponent(TemporaryAttachmentSessionsManager.class);
    }

    /**
     * Read the list of attachment that should be added from {@link EditForm#getTemporaryUploadedFiles()} and attach
     * them to the current document if they can be found in the {@link TemporaryAttachmentSessionsManager}.
     *
     * @param editForm the form from which to read the list of files.
     * @since 14.3RC1
     */
    public void readTemporaryUploadedFiles(EditForm editForm)
    {
        getTemporaryAttachmentManager().attachTemporaryAttachmentsInDocument(this,
            editForm.getTemporaryUploadedFiles());
    }

    /**
     * Adds objects, applies property updates and removes objects as specified in the form.
     *
     * @param eform The form from which the values shall be read.
     * @param context The XWiki context.
     * @throws XWikiException If an error occurs.
     * @since 14.0RC1
     */
    public void readAddedUpdatedAndRemovedObjectsFromForm(EditForm eform, XWikiContext context) throws XWikiException
    {
        // We add the new objects that have been submitted in the form, before filling them with their values.
        Map<String, List<Integer>> objectsToAdd = eform.getObjectsToAdd();
        for (String className : objectsToAdd.keySet()) {
            DocumentReference classReference = resolveClassReference(className);
            List<Integer> classIds = objectsToAdd.get(className);
            for (Integer classId : classIds) {
                // we ensure that the object has not been added yet, for example because of the update or create.
                getXObject(classReference, classId, true, context);
            }
        }

        ObjectPolicyType objectPolicy = eform.getObjectPolicy();
        if (objectPolicy == null || objectPolicy.equals(ObjectPolicyType.UPDATE)) {
            readObjectsFromForm(eform, context);
        } else if (objectPolicy.equals(ObjectPolicyType.UPDATE_OR_CREATE)) {
            readObjectsFromFormUpdateOrCreate(eform, context);
        }

        // remove xobjects
        Map<String, List<Integer>> objectsToRemove = eform.getObjectsToRemove();
        for (String className : objectsToRemove.keySet()) {
            DocumentReference classReference = resolveClassReference(className);
            List<Integer> classIds = objectsToRemove.get(className);
            for (Integer classId : classIds) {
                BaseObject xObject = getXObject(classReference, classId);
                if (xObject != null) {
                    removeXObject(xObject);
                }
            }
        }
    }

    /**
     * @since 2.2M1
     */
    public void readFromTemplate(DocumentReference templateDocumentReference, XWikiContext context)
        throws XWikiException
    {
        if (templateDocumentReference != null) {
            String content = getContent();
            if (!content.equals("\n") && !content.equals("") && !isNew()) {
                Object[] args = {getDefaultEntityReferenceSerializer().serialize(getDocumentReference())};
                throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                    XWikiException.ERROR_XWIKI_APP_DOCUMENT_NOT_EMPTY,
                    "Cannot add a template to document {0} because it already has content", null, args);
            } else {
                XWiki xwiki = context.getWiki();
                XWikiDocument templatedoc = xwiki.getDocument(templateDocumentReference, context);
                if (templatedoc.isNew()) {
                    Object[] args = {getDefaultEntityReferenceSerializer().serialize(templateDocumentReference),
                        getCompactEntityReferenceSerializer().serialize(getDocumentReference())};
                    throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                        XWikiException.ERROR_XWIKI_APP_TEMPLATE_DOES_NOT_EXIST,
                        "Template document {0} does not exist when adding to document {1}", null, args);
                } else {
                    setTemplateDocumentReference(templateDocumentReference);
                    setTitle(templatedoc.getTitle());
                    setContent(templatedoc.getContent());

                    // Set the new document syntax as the syntax of the template since the template content
                    // is copied into the new document
                    setSyntax(templatedoc.getSyntax());

                    // If the parent is not set in the current document set the template parent as the parent.
                    if (getParentReference() == null) {
                        setParentReference(templatedoc.getRelativeParentReference());
                    }

                    if (isNew()) {
                        // We might have received the objects from the cache and the template objects might have been
                        // copied already we need to remove them
                        setXObjects(new TreeMap<DocumentReference, List<BaseObject>>());
                    }
                    // Merge the external objects.
                    // Currently the choice is not to merge the base class and object because it is not the preferred
                    // way of using external classes and objects.
                    mergeXObjects(templatedoc);

                    // Copy the attachments from the template document, but don't overwrite existing attachments because
                    // the user can add attachments from the WYSIWYG editor before the save button is clicked (and thus
                    // before the template is applied).
                    copyAttachments(templatedoc, false, true);
                }
            }
        }
    }

    /**
     * Use the document passed as parameter as the new identity for the current document.
     *
     * @param document the document containing the new identity
     */
    public void clone(XWikiDocument document)
    {
        this.id = document.id;

        setDocumentReference(document.getDocumentReference());
        setRCSVersion(document.getRCSVersion());
        setDocumentArchive(document.getDocumentArchive());
        setAuthorReference(document.getAuthorReference());
        setContentAuthorReference(document.getContentAuthorReference());
        setContent(document.getContent());
        setCreationDate(document.getCreationDate());
        setDate(document.getDate());
        setCustomClass(document.getCustomClass());
        setContentUpdateDate(document.getContentUpdateDate());
        setTitle(document.getTitle());
        setFormat(document.getFormat());
        setFromCache(document.isFromCache());
        setElements(document.getElements());
        setMeta(document.getMeta());
        setMostRecent(document.isMostRecent());
        setNew(document.isNew());
        setStore(document.getStore());
        setTemplateDocumentReference(document.getTemplateDocumentReference());
        setParentReference(document.getRelativeParentReference());
        setCreatorReference(document.getCreatorReference());
        setDefaultLocale(document.getDefaultLocale());
        setDefaultTemplate(document.getDefaultTemplate());
        setValidationScript(document.getValidationScript());
        setLocale(document.getLocale());
        setXClass(document.getXClass().clone());
        setXClassXML(document.getXClassXML());
        setComment(document.getComment());
        setMinorEdit(document.isMinorEdit());
        setSyntax(document.getSyntax());
        setHidden(document.isHidden());
        setEnforceRequiredRights(document.isEnforceRequiredRights());

        cloneXObjects(document);
        cloneAttachments(document);

        setContentDirty(document.isContentDirty());
        setMetaDataDirty(document.isMetaDataDirty());

        this.elements = document.elements;

        this.originalDocument = document.originalDocument;
    }

    @Override
    public XWikiDocument clone()
    {
        return cloneInternal(getDocumentReference(), true, false);
    }

    /**
     * Duplicate this document and give it a new name.
     *
     * @since 2.2.3
     */
    public XWikiDocument duplicate(DocumentReference newDocumentReference)
    {
        return cloneInternal(newDocumentReference, false, false);
    }

    private void cloneDocumentArchive(XWikiDocument originalDocument) throws XWikiException
    {
        XWikiDocumentArchive documentArchive = originalDocument.getDocumentArchive();
        if (documentArchive != null) {
            this.setDocumentArchive(documentArchive.clone(this.getId(), getXWikiContext()));
        }
    }

    private XWikiDocument cloneInternal(DocumentReference newDocumentReference, boolean keepsIdentity,
        boolean cloneArchive)
    {
        XWikiDocument doc = null;

        try {
            Constructor<? extends XWikiDocument> constructor = getClass().getConstructor(DocumentReference.class);
            doc = constructor.newInstance(newDocumentReference);

            if (keepsIdentity && getDocumentReference().equals(doc.getDocumentReference())) {
                doc.setChangeTracked(isChangeTracked());
            }

            // Make sure the coordinate of the document is fully accurate before any other manipulation
            doc.setLocale(getLocale());

            // use version field instead of getRCSVersion because it returns "1.1" if version==null.
            doc.version = this.version;
            doc.id = this.id;
            if (cloneArchive) {
                doc.cloneDocumentArchive(this);
            } else {
                // Without this explicit initialization, it is possible for the archive to be incorrectly initialized.
                // For instance, with the archive of the cloned document.
                // Here we guarantee that further calls of APIs to get the archive will properly populate the data.
                doc.setDocumentArchive((XWikiDocumentArchive) null);
            }
            doc.getAuthors().copyAuthors(getAuthors());
            doc.setCreationDate(getCreationDate());
            doc.setDate(getDate());
            doc.setCustomClass(getCustomClass());
            doc.setContentUpdateDate(getContentUpdateDate());
            doc.setTitle(getTitle());
            doc.setFormat(getFormat());
            doc.setFromCache(isFromCache());
            doc.setElements(getElements());
            doc.setMeta(getMeta());
            doc.setMostRecent(isMostRecent());
            doc.setNew(isNew());
            doc.setStore(getStore());
            doc.setTemplateDocumentReference(getTemplateDocumentReference());
            doc.setParentReference(getRelativeParentReference());
            doc.setDefaultLocale(getDefaultLocale());
            doc.setDefaultTemplate(getDefaultTemplate());
            doc.setValidationScript(getValidationScript());
            doc.setComment(getComment());
            doc.setMinorEdit(isMinorEdit());
            doc.setHidden(isHidden());
            doc.setRestricted(isRestricted());
            doc.setEnforceRequiredRights(isEnforceRequiredRights());

            if (this.xClass != null) {
                doc.setXClass(this.xClass.clone(true));
            }

            if (keepsIdentity) {
                doc.setXClassXML(getXClassXML());
                doc.cloneAttachments(this);
            } else {
                doc.getXClass().setCustomMapping(null);
                doc.copyAttachments(this);
            }
            doc.cloneXObjects(this, keepsIdentity);

            // Keep the same content and more importantly the same cache of parser/prepared content (it will become
            // different if the content/syntax is modified in any of the documents)
            doc.content = this.content;

            doc.setContentDirty(isContentDirty());
            doc.setMetaDataDirty(isMetaDataDirty());

            doc.elements = this.elements;

            doc.originalDocument = this.originalDocument;
        } catch (Exception e) {
            // This should not happen
            LOGGER.error("Exception while cloning document", e);
        }
        return doc;
    }

    /**
     * Clone attachments from another document. This implementation expects that this document is the same as the other
     * document and thus attachments will be saved in the database in the same place as the ones which they are cloning.
     *
     * @param sourceDocument an XWikiDocument to copy attachments from
     */
    private void cloneAttachments(final XWikiDocument sourceDocument)
    {
        this.getAttachmentList().clear();
        for (XWikiAttachment attach : sourceDocument.getAttachmentList()) {
            XWikiAttachment newAttach = attach.clone();

            setAttachment(newAttach);
        }
    }

    /**
     * Copy attachments from one document to another. This implementation expects that you are copying the attachment
     * from one document to another and thus it should be saved separately from the original in the database.
     *
     * @param sourceDocument an XWikiDocument to copy attachments from
     */
    public void copyAttachments(XWikiDocument sourceDocument)
    {
        copyAttachments(sourceDocument, true);
    }

    /**
     * Copy attachments from one document to another. This implementation expects that you are copying the attachment
     * from one document to another and thus it should be saved separately from the original in the database.
     *
     * @param sourceDocument an XWikiDocument to copy attachments from
     * @param overwrite whether to overwrite the existing attachments or not
     * @since 8.4.6
     * @since 9.6RC1
     */
    private void copyAttachments(XWikiDocument sourceDocument, boolean overwrite)
    {
        copyAttachments(sourceDocument, overwrite, false);
    }

    private void copyAttachments(XWikiDocument sourceDocument, boolean overwrite, boolean reset)
    {
        if (overwrite) {
            // Note: when clearing the attachment list, we automatically mark the document's metadata as dirty.
            getAttachmentList().clear();
        }

        for (XWikiAttachment attachment : sourceDocument.getAttachmentList()) {
            if (overwrite || this.getAttachment(attachment.getFilename()) == null) {
                try {
                    copyAttachment(attachment, reset);
                } catch (XWikiException e) {
                    LOGGER.warn("Cannot copy attachment [{}] from [{}] to [{}]. Root cause is [{}].",
                        attachment.getFilename(), sourceDocument.getDocumentReference(), this.getDocumentReference(),
                        ExceptionUtils.getRootCauseMessage(e));
                    // Skip this attachment because we cannot load its content.
                    continue;
                }
            }
        }
    }

    /**
     * Copies the given attachment to this document.
     * 
     * @param attachment the source attachment to be copied to this document
     * @param reset whether to reset or not the attachment meta data that is specific to the source (version, author,
     *            date)
     * @throws XWikiException if loading the content of the given attachment fails
     */
    private void copyAttachment(XWikiAttachment attachment, boolean reset) throws XWikiException
    {
        XWikiContext xcontext = getXWikiContext();
        XWikiAttachment newAttachment = attachment.clone();

        // Make sure we copy the attachment content also, not just its meta data. For this we need to load
        // the attachment content from the source document. Note that the owner document will be overwritten
        // below when we call setAttachment().
        newAttachment.setDoc(attachment.getDoc(), false);
        newAttachment.loadAttachmentContent(xcontext);
        // We need to set the content of the attachment to be dirty because the dirty bit is used to signal
        // that there is a reason to save the copied attachment, otherwise the copied attachment will be
        // empty since the original attachment content is not modified in this operation.
        newAttachment.getAttachment_content().setContentDirty(true);

        if (reset) {
            // Reset the meta data that is specific to the original attachment (version, author, date).
            newAttachment.setRCSVersion(null);
            newAttachment.setAuthorReference(xcontext.getUserReference());
            newAttachment.setDate(new Date());
        }

        // Add the attachment copy to the list of attachments of this document.
        setAttachment(newAttachment);
    }

    /**
     * Load attachment content from database.
     *
     * @param context the XWiki context
     * @throws XWikiException when failing to load attachments
     * @since 5.3M2
     */
    public void loadAttachmentsContent(XWikiContext context) throws XWikiException
    {
        for (XWikiAttachment attachment : getAttachmentList()) {
            attachment.loadAttachmentContent(context);
        }
    }

    /**
     * Same as {@link #loadAttachmentContent(XWikiAttachment, XWikiContext)} but in some context we don't really care if
     * an attachment content could not be loaded (we are going to overwrite or ignore it).
     * 
     * @param context the XWiki context
     * @since 10.1RC1
     */
    public void loadAttachmentsContentSafe(XWikiContext context)
    {
        for (XWikiAttachment attachment : getAttachmentList()) {
            try {
                attachment.loadAttachmentContent(context);
            } catch (XWikiException e) {
                LOGGER.warn("Failed to load attachment [{}]: {}", attachment.getReference(),
                    ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }

    public void loadAttachments(XWikiContext context) throws XWikiException
    {
        for (XWikiAttachment attachment : getAttachmentList()) {
            attachment.loadAttachmentContent(context);
            attachment.loadArchive(context);
        }
    }

    /**
     * Indicates whether some other document is "equal to" this one.
     * <p>
     * This ignores the {@link #isRestricted()} property as it is not considered to be part of the data.
     *
     * @param object the document to compare to
     * @return {@code true} if this document is the same as the object argument; {@code false} otherwise
     */
    @Override
    public boolean equals(Object object)
    {
        // Same Java object, they sure are equal
        if (this == object) {
            return true;
        }

        // Reference/language (document identifier)

        XWikiDocument doc = (XWikiDocument) object;
        if (!getDocumentReference().equals(doc.getDocumentReference())) {
            return false;
        }

        if (!getDefaultLocale().equals(doc.getDefaultLocale())) {
            return false;
        }

        if (!getLocale().equals(doc.getLocale())) {
            return false;
        }

        if (getTranslation() != doc.getTranslation()) {
            return false;
        }

        // Authors

        if (ObjectUtils.notEqual(getAuthorReference(), doc.getAuthorReference())) {
            return false;
        }

        if (ObjectUtils.notEqual(getContentAuthorReference(), doc.getContentAuthorReference())) {
            return false;
        }

        if (ObjectUtils.notEqual(getCreatorReference(), doc.getCreatorReference())) {
            return false;
        }

        // Version

        if (!getVersion().equals(doc.getVersion())) {
            return false;
        }

        if (getDate().getTime() != doc.getDate().getTime()) {
            return false;
        }

        if (getContentUpdateDate().getTime() != doc.getContentUpdateDate().getTime()) {
            return false;
        }

        if (getCreationDate().getTime() != doc.getCreationDate().getTime()) {
            return false;
        }

        if (!getComment().equals(doc.getComment())) {
            return false;
        }

        if (isMinorEdit() != doc.isMinorEdit()) {
            return false;
        }

        // Datas

        if (!equalsData(doc)) {
            return false;
        }

        // We consider that 2 documents are still equal even when they have different original
        // documents (see getOriginalDocument() for more details as to what is an original
        // document).

        return true;
    }

    /**
     * Same as {@link #equals(Object)} but only for actual datas of the document.
     * <p>
     * The goal being to compare two versions of the same document this method skip every version/reference/author
     * related information. For example it allows to compare a document coming from a another wiki and easily check if
     * thoses actually are the same thing whatever the plumbing differences.
     *
     * @param otherDocument the document to compare
     * @return true if bith documents have the same datas
     * @since 4.1.1
     */
    public boolean equalsData(XWikiDocument otherDocument)
    {
        // Same Java object, they sure are equal
        if (this == otherDocument) {
            return true;
        }

        if (ObjectUtils.notEqual(getParentReference(), otherDocument.getParentReference())) {
            return false;
        }

        if (!getFormat().equals(otherDocument.getFormat())) {
            return false;
        }

        if (!getTitle().equals(otherDocument.getTitle())) {
            return false;
        }

        if (!getContent().equals(otherDocument.getContent())) {
            return false;
        }

        if (!getDefaultTemplate().equals(otherDocument.getDefaultTemplate())) {
            return false;
        }

        if (!getValidationScript().equals(otherDocument.getValidationScript())) {
            return false;
        }

        if (ObjectUtils.notEqual(getSyntax(), otherDocument.getSyntax())) {
            return false;
        }

        if (isHidden() != otherDocument.isHidden()) {
            return false;
        }

        if (isEnforceRequiredRights() != otherDocument.isEnforceRequiredRights()) {
            return false;
        }

        // XClass

        if (!getXClass().equals(otherDocument.getXClass())) {
            return false;
        }

        // XObjects

        Set<DocumentReference> myObjectClassReferences = getXObjects().keySet();
        Set<DocumentReference> otherObjectClassReferences = otherDocument.getXObjects().keySet();
        if (!myObjectClassReferences.equals(otherObjectClassReferences)) {
            return false;
        }

        for (DocumentReference reference : myObjectClassReferences) {
            List<BaseObject> myObjects = getXObjects(reference);
            List<BaseObject> otherObjects = otherDocument.getXObjects(reference);
            if (myObjects.size() != otherObjects.size()) {
                return false;
            }
            for (int i = 0; i < myObjects.size(); i++) {
                if ((myObjects.get(i) == null && otherObjects.get(i) != null)
                    || (myObjects.get(i) != null && otherObjects.get(i) == null)) {
                    return false;
                }
                if (myObjects.get(i) == null && otherObjects.get(i) == null) {
                    continue;
                }
                if (!myObjects.get(i).equals(otherObjects.get(i))) {
                    return false;
                }
            }
        }

        // Attachments
        List<XWikiAttachment> attachments = getAttachmentList();
        List<XWikiAttachment> otherAttachments = otherDocument.getAttachmentList();
        if (attachments.size() != otherAttachments.size()) {
            return false;
        }
        for (XWikiAttachment attachment : attachments) {
            XWikiAttachment otherAttachment = otherDocument.getAttachment(attachment.getFilename());
            try {
                if (otherAttachment == null || !attachment.equalsData(otherAttachment, null)) {
                    return false;
                }
            } catch (XWikiException e) {
                throw new RuntimeException(
                    String.format("Failed to compare attachments with reference [%s]", attachment.getReference()), e);
            }
        }

        return true;
    }

    /**
     * Retrieve the document in the current context language as an XML string. The rendrered document content and all
     * XObjects are included. Document attachments and archived versions are excluded. You should prefer
     * toXML(OutputStream, true, true, false, false, XWikiContext)} or toXML(com.xpn.xwiki.util.XMLWriter, true, true,
     * false, false, XWikiContext) on the translated document when possible to reduce memory load.
     *
     * @param context current XWikiContext
     * @return a string containing an XML representation of the document in the current context language
     * @throws XWikiException when an error occurs during wiki operation
     */
    public String getXMLContent(XWikiContext context) throws XWikiException
    {
        XWikiDocument tdoc = getTranslatedDocument(context);
        return tdoc.toXML(true, true, false, false, context);
    }

    /**
     * Retrieve the document as an XML string. All XObject are included. Rendered content, attachments and archived
     * version are excluded. You should prefer toXML(OutputStream, true, false, false, false, XWikiContext)} or
     * toXML(com.xpn.xwiki.util.XMLWriter, true, false, false, false, XWikiContext) when possible to reduce memory load.
     *
     * @param context current XWikiContext
     * @return a string containing an XML representation of the document
     * @throws XWikiException when an error occurs during wiki operation
     */
    public String toXML(XWikiContext context) throws XWikiException
    {
        return toXML(true, false, false, false, context);
    }

    /**
     * Retrieve the document as an XML string. All XObjects attachments and archived version are included. Rendered
     * content is excluded. You should prefer toXML(OutputStream, true, false, true, true, XWikiContext)} or
     * toXML(com.xpn.xwiki.util.XMLWriter, true, false, true, true, XWikiContext) when possible to reduce memory load.
     *
     * @param context current XWikiContext
     * @return a string containing an XML representation of the document
     * @throws XWikiException when an error occurs during wiki operation
     */
    public String toFullXML(XWikiContext context) throws XWikiException
    {
        return toXML(true, false, true, true, context);
    }

    /**
     * Serialize the document into a new entry of an ZipOutputStream in XML format. All XObjects and attachments are
     * included. Rendered content is excluded.
     *
     * @param zos the ZipOutputStream to write to
     * @param zipname the name of the new entry to create
     * @param withVersions if true, also include archived version of the document
     * @param context current XWikiContext
     * @throws XWikiException when an error occurs during xwiki operations
     * @throws IOException when an error occurs during streaming operations
     * @since 2.3M2
     * @deprecated
     */
    @Deprecated(since = "4.1M2")
    public void addToZip(ZipOutputStream zos, String zipname, boolean withVersions, XWikiContext context)
        throws XWikiException, IOException
    {
        ZipEntry zipentry = new ZipEntry(zipname);
        zos.putNextEntry(zipentry);
        toXML(zos, true, false, true, withVersions, context);
        zos.closeEntry();
    }

    /**
     * Serialize the document into a new entry of an ZipOutputStream in XML format. The new entry is named
     * 'LastSpaceName/DocumentName'. All XObjects and attachments are included. Rendered content is excluded.
     *
     * @param zos the ZipOutputStream to write to
     * @param withVersions if true, also include archived version of the document
     * @param context current XWikiContext
     * @throws XWikiException when an error occurs during xwiki operations
     * @throws IOException when an error occurs during streaming operations
     * @since 2.3M2
     * @deprecated
     */
    @Deprecated(since = "4.2M2")
    public void addToZip(ZipOutputStream zos, boolean withVersions, XWikiContext context)
        throws XWikiException, IOException
    {
        String zipname =
            getDocumentReference().getLastSpaceReference().getName() + "/" + getDocumentReference().getName();
        String language = getLanguage();
        if (!StringUtils.isEmpty(language)) {
            zipname += "." + language;
        }
        addToZip(zos, zipname, withVersions, context);
    }

    /**
     * Serialize the document into a new entry of an ZipOutputStream in XML format. The new entry is named
     * 'LastSpaceName/DocumentName'. All XObjects, attachments and archived versions are included. Rendered content is
     * excluded.
     *
     * @param zos the ZipOutputStream to write to
     * @param context current XWikiContext
     * @throws XWikiException when an error occurs during xwiki operations
     * @throws IOException when an error occurs during streaming operations
     * @since 2.3M2
     * @deprecated
     */
    @Deprecated(since = "4.1M2")
    public void addToZip(ZipOutputStream zos, XWikiContext context) throws XWikiException, IOException
    {
        addToZip(zos, true, context);
    }

    /**
     * Serialize the document to an XML string. You should prefer
     * {@link #toXML(OutputStream, boolean, boolean, boolean, boolean, XWikiContext)} or
     * {@link #toXML(com.xpn.xwiki.internal.xml.XMLWriter, boolean, boolean, boolean, boolean, XWikiContext)} when
     * possible to reduce memory load.
     *
     * @param bWithObjects include XObjects
     * @param bWithRendering include the rendered content
     * @param bWithAttachmentContent include attachments content
     * @param bWithVersions include archived versions
     * @param context current XWikiContext
     * @return a string containing an XML representation of the document
     * @throws XWikiException when an errors occurs during wiki operations
     */
    public String toXML(boolean bWithObjects, boolean bWithRendering, boolean bWithAttachmentContent,
        boolean bWithVersions, XWikiContext context) throws XWikiException
    {
        StringWriter writer = new StringWriter();

        toXML(new DefaultWriterOutputTarget(writer), bWithObjects, bWithRendering, bWithAttachmentContent,
            bWithVersions, true, context != null ? context.getWiki().getEncoding() : StandardCharsets.UTF_8.name());

        return writer.toString();
    }

    /**
     * Serialize the document to an XML {@link DOMDocument}. All XObject are included. Rendered content, attachments and
     * archived version are excluded. You should prefer toXML(OutputStream, true, false, false, false, XWikiContext)} or
     * toXML(com.xpn.xwiki.util.XMLWriter, true, false, false, false, XWikiContext) when possible to reduce memory load.
     *
     * @param context current XWikiContext
     * @return a {@link DOMDocument} containing the serialized document.
     * @throws XWikiException when an errors occurs during wiki operations
     * @deprecated use {@link #toXML(OutputTarget, boolean, boolean, boolean, boolean, boolean, String)} instead
     */
    @Deprecated(since = "9.0RC1")
    public Document toXMLDocument(XWikiContext context) throws XWikiException
    {
        return toXMLDocument(true, false, false, false, context);
    }

    /**
     * Serialize the document to an XML {@link DOMDocument}. You should prefer
     * {@link #toXML(OutputStream, boolean, boolean, boolean, boolean, XWikiContext)} or
     * {@link #toXML(com.xpn.xwiki.internal.xml.XMLWriter, boolean, boolean, boolean, boolean, XWikiContext)} when
     * possible to reduce memory load.
     *
     * @param bWithObjects include XObjects
     * @param bWithRendering include the rendered content
     * @param bWithAttachmentContent include attachments content
     * @param bWithVersions include archived versions
     * @param context current XWikiContext
     * @return a {@link DOMDocument} containing the serialized document.
     * @throws XWikiException when an errors occurs during wiki operations
     * @deprecated use {@link #toXML(OutputTarget, boolean, boolean, boolean, boolean, boolean, String)} instead
     */
    @Deprecated(since = "9.0RC1")
    public Document toXMLDocument(boolean bWithObjects, boolean bWithRendering, boolean bWithAttachmentContent,
        boolean bWithVersions, XWikiContext context) throws XWikiException
    {
        Document doc = new DOMDocument();
        DOMXMLWriter wr = new DOMXMLWriter(doc, new OutputFormat("", true, context.getWiki().getEncoding()));

        try {
            toXML(wr, bWithObjects, bWithRendering, bWithAttachmentContent, bWithVersions, context);
            return doc;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Serialize the document to a {@link com.xpn.xwiki.internal.xml.XMLWriter}.
     *
     * @param bWithObjects include XObjects
     * @param bWithRendering include the rendered content
     * @param bWithAttachmentContent include attachments content
     * @param bWithVersions include archived versions
     * @param context current XWikiContext
     * @throws XWikiException when an errors occurs during wiki operations
     * @throws IOException when an errors occurs during streaming operations
     * @since 2.3M2
     * @deprecated use {@link #toXML(OutputTarget, boolean, boolean, boolean, boolean, boolean, String)} instead
     */
    @Deprecated(since = "9.0RC1")
    public void toXML(XMLWriter wr, boolean bWithObjects, boolean bWithRendering, boolean bWithAttachmentContent,
        boolean bWithVersions, XWikiContext context) throws XWikiException, IOException
    {
        // IMPORTANT: we don't use directly XMLWriter's SAX apis here because it's not really working well
        DocumentResult domResult = new DocumentResult();

        toXML(new DefaultResultOutputTarget(domResult), bWithObjects, bWithRendering, bWithAttachmentContent,
            bWithVersions, true, context != null ? context.getWiki().getEncoding() : StandardCharsets.UTF_8.name());

        wr.write(domResult.getDocument().getRootElement());
    }

    /**
     * Serialize the document to an OutputStream.
     *
     * @param bWithObjects include XObjects
     * @param bWithRendering include the rendered content
     * @param bWithAttachmentContent include attachments content
     * @param bWithVersions include archived versions
     * @param context current XWikiContext
     * @throws XWikiException when an errors occurs during wiki operations
     * @throws IOException when an errors occurs during streaming operations
     * @since 2.3M2
     */
    public void toXML(OutputStream out, boolean bWithObjects, boolean bWithRendering, boolean bWithAttachmentContent,
        boolean bWithVersions, XWikiContext context) throws XWikiException, IOException
    {
        toXML(new DefaultOutputStreamOutputTarget(out), bWithObjects, bWithRendering, bWithAttachmentContent,
            bWithVersions, true, context != null ? context.getWiki().getEncoding() : StandardCharsets.UTF_8.name());
    }

    /**
     * Serialize the document to an OutputStream.
     *
     * @param out the output where to write the XML
     * @param bWithObjects include XObjects
     * @param bWithRendering include the rendered content
     * @param bWithAttachmentContent include attachments content
     * @param bWithVersions include archived versions
     * @param format true if the XML should be formated
     * @param encoding the encoding to use to write the XML
     * @throws XWikiException when an errors occurs during wiki operations
     * @since 9.0RC1
     */
    public void toXML(OutputTarget out, boolean bWithObjects, boolean bWithRendering, boolean bWithAttachmentContent,
        boolean bWithVersions, boolean format, String encoding) throws XWikiException
    {
        // Input
        DocumentInstanceInputProperties documentProperties = new DocumentInstanceInputProperties();
        documentProperties.setWithWikiObjects(bWithObjects);
        documentProperties.setWithWikiDocumentContentHTML(bWithRendering);
        documentProperties.setWithWikiAttachmentsContent(bWithAttachmentContent);
        documentProperties.setWithJRCSRevisions(bWithVersions);
        documentProperties.setWithRevisions(false);

        // Output
        XAROutputProperties xarProperties = new XAROutputProperties();
        xarProperties.setPreserveVersion(bWithVersions);
        xarProperties.setEncoding(encoding);
        xarProperties.setFormat(format);
        xarProperties.setTarget(out);

        toXML(documentProperties, xarProperties);
    }

    /**
     * Serialize the document to an OutputStream.
     *
     * @param xarProperties the configuration of the output filter
     * @param documentProperties the configuration of the input filter
     * @throws XWikiException when an errors occurs during wiki operations
     * @since 13.8RC1
     */
    public void toXML(DocumentInstanceInputProperties documentProperties, XAROutputProperties xarProperties)
        throws XWikiException
    {
        try {
            Utils.getComponent(XWikiDocumentFilterUtils.class).exportEntity(this, xarProperties.getTarget(),
                xarProperties, documentProperties);
        } catch (Exception e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_DOC, XWikiException.ERROR_XWIKI_DOC_EXPORT,
                "Error serializing XML", e, null);
        }
    }

    protected String encodedXMLStringAsUTF8(String xmlString)
    {
        if (xmlString == null) {
            return "";
        }

        int length = xmlString.length();
        char character;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            character = xmlString.charAt(i);
            switch (character) {
                case '&':
                    result.append("&amp;");
                    break;
                case '"':
                    result.append("&quot;");
                    break;
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                case '\n':
                    result.append("\n");
                    break;
                case '\r':
                    result.append("\r");
                    break;
                case '\t':
                    result.append("\t");
                    break;
                default:
                    if (character < 0x20) {
                    } else if (character > 0x7F) {
                        result.append("&#x");
                        result.append(Integer.toHexString(character).toUpperCase());
                        result.append(";");
                    } else {
                        result.append(character);
                    }
                    break;
            }
        }

        return result.toString();
    }

    protected String getElement(Element docel, String name)
    {
        Element el = docel.element(name);
        if (el == null) {
            return "";
        } else {
            return el.getText();
        }
    }

    public void fromXML(String xml) throws XWikiException
    {
        fromXML(xml, false);
    }

    public void fromXML(InputStream is) throws XWikiException
    {
        fromXML(is, false);
    }

    public void fromXML(InputSource source, boolean withArchive) throws XWikiException
    {
        // Output
        DocumentInstanceOutputProperties documentProperties = new DocumentInstanceOutputProperties();
        XWikiContext xcontext = getXWikiContext();
        if (xcontext != null) {
            documentProperties.setDefaultReference(getXWikiContext().getWikiReference());
        }

        // Input
        XARInputProperties xarProperties = new XARInputProperties();
        xarProperties.setWithHistory(withArchive);

        try {
            Utils.getComponent(XWikiDocumentFilterUtils.class).importEntity(XWikiDocument.class, this, source,
                xarProperties, documentProperties);
        } catch (Exception e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_DOC, XWikiException.ERROR_DOC_XML_PARSING,
                "Error parsing xml", e, null);
        }

        // We have been reading from XML so the document does not need a new version when saved
        setMetaDataDirty(false);
        setContentDirty(false);
    }

    public void fromXML(String source, boolean withArchive) throws XWikiException
    {
        fromXML(new StringInputSource(source), withArchive);
    }

    public void fromXML(InputStream source, boolean withArchive) throws XWikiException
    {
        fromXML(new DefaultInputStreamInputSource(source), withArchive);
    }

    /**
     * @deprecated use {@link #fromXML(InputStream)} instead
     */
    @Deprecated(since = "9.0RC1")
    public void fromXML(Document domdoc, boolean withArchive) throws XWikiException
    {
        // Serialize the Document (could not find a way to convert a dom4j Document into a usable StAX source)
        StringWriter writer = new StringWriter();
        try {
            org.dom4j.io.XMLWriter domWriter = new org.dom4j.io.XMLWriter(writer);
            domWriter.write(domdoc);
            domWriter.flush();
        } catch (IOException e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_DOC, XWikiException.ERROR_DOC_XML_PARSING,
                "Error parsing xml", e, null);
        }

        // Actually parse the XML
        fromXML(writer.toString(), withArchive);
    }

    /**
     * Check if provided xml document is a wiki document.
     *
     * @param domdoc the xml document.
     * @return true if provided xml document is a wiki document.
     */
    public static boolean containsXMLWikiDocument(Document domdoc)
    {
        return domdoc.getRootElement().getName().equals(XarDocumentModel.ELEMENT_DOCUMENT);
    }

    public void setAttachmentList(List<XWikiAttachment> list)
    {
        // For backwards compatibility reasons (and in general), we need to allow callers to do something like
        // setAttachmentList(getAttachmentList())
        if (this.attachmentList != list) {
            this.attachmentList.clear();
            this.attachmentList.addAll(list);
        }
    }

    public List<XWikiAttachment> getAttachmentList()
    {
        return this.attachmentList;
    }

    /**
     * @deprecated should not be used, save the document instead
     */
    @Deprecated
    public void saveAllAttachments(XWikiContext context) throws XWikiException
    {
        saveAllAttachments(true, true, context);
    }

    /**
     * @deprecated should not be used, save the document instead
     */
    @Deprecated
    public void saveAllAttachments(boolean updateParent, boolean transaction, XWikiContext context)
        throws XWikiException
    {
        for (XWikiAttachment attachment : this.attachmentList) {
            saveAttachmentContent(attachment, false, transaction, context);
        }

        // Save the document
        if (updateParent) {
            context.getWiki().saveDocument(this, context);
        }
    }

    /**
     * @deprecated should not be used, save the document instead
     */
    @Deprecated
    public void saveAttachmentsContent(List<XWikiAttachment> attachments, XWikiContext context) throws XWikiException
    {
        for (XWikiAttachment attachment : attachments) {
            saveAttachmentContent(attachment, context);
        }
    }

    /**
     * @deprecated should not be used, save the document instead
     */
    @Deprecated
    public void saveAttachmentContent(XWikiAttachment attachment, XWikiContext context) throws XWikiException
    {
        saveAttachmentContent(attachment, true, true, context);
    }

    /**
     * @deprecated should not be used, save the document instead
     */
    @Deprecated
    public void saveAttachmentContent(XWikiAttachment attachment, boolean updateParent, boolean transaction,
        XWikiContext context) throws XWikiException
    {
        String currentWiki = context.getWikiId();
        try {
            // We might need to switch database to
            // get the translated content
            if (getDatabase() != null) {
                context.setWikiId(getDatabase());
            }

            // Save the attachment
            XWikiAttachmentStoreInterface store =
                resolveXWikiAttachmentStoreInterface(attachment.getContentStore(), context);
            store.saveAttachmentContent(attachment, false, context, transaction);

            // We need to make sure there is a version upgrade
            setMetaDataDirty(true);

            // Save the document
            if (updateParent) {
                context.getWiki().saveDocument(this, context);
            }
        } catch (OutOfMemoryError e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_APP, XWikiException.ERROR_XWIKI_APP_JAVA_HEAP_SPACE,
                "Out Of Memory Exception");
        } finally {
            if (currentWiki != null) {
                context.setWikiId(currentWiki);
            }
        }
    }

    /**
     * @deprecated use {@link XWikiAttachment#loadContent(XWikiContext)}
     */
    @Deprecated(since = "9.9RC1")
    public void loadAttachmentContent(XWikiAttachment attachment, XWikiContext context) throws XWikiException
    {
        String database = context.getWikiId();
        try {
            // We might need to switch database to
            // get the translated content
            if (getDatabase() != null) {
                context.setWikiId(getDatabase());
            }

            attachment.loadAttachmentContent(context);
        } finally {
            if (database != null) {
                context.setWikiId(database);
            }
        }
    }

    /**
     * Remove the attachment from the document attachment list and put it in the list of attachments to remove for next
     * document save.
     * <p>
     * The attachment will be move to recycle bin.
     *
     * @param attachment the attachment to remove
     * @return the removed attachment, null if none could be found
     * @since 5.2M1
     */
    public XWikiAttachment removeAttachment(XWikiAttachment attachment)
    {
        return removeAttachment(attachment, true);
    }

    /**
     * Remove the attachment from the document attachment list and put it in the list of attachments to remove for next
     * document save.
     *
     * @param attachmentToRemove the attachment to remove
     * @param toRecycleBin indicate if the attachment should be moved to recycle bin
     * @return the removed attachment, null if none could be found
     * @since 5.2M1
     */
    public XWikiAttachment removeAttachment(XWikiAttachment attachmentToRemove, boolean toRecycleBin)
    {
        if (this.attachmentList.remove(attachmentToRemove)) {
            this.attachmentsToRemove.add(new XWikiAttachmentToRemove(attachmentToRemove, toRecycleBin));
            setMetaDataDirty(true);
        } else {
            attachmentToRemove = null;
        }
        return attachmentToRemove;
    }

    /**
     * @return the attachment planned for removal
     */
    public List<XWikiAttachmentToRemove> getAttachmentsToRemove()
    {
        return this.attachmentsToRemove;
    }

    /**
     * Clear the list of attachments planned for removal.
     */
    public void clearAttachmentsToRemove()
    {
        this.attachmentsToRemove.clear();
    }

    /**
     * Get the wiki document references pointing to this document.
     * <p>
     * Theses links are stored in the Solr search core when the document is indexed. You can use "backlinks" in
     * XWikiPreferences or "xwiki.backlinks" in xwiki.cfg file to enable links storage in the database.
     * <p>
     * Since 14.8RC1, this method return all backlinked documents and not just those located in the context wiki.
     *
     * @param context the XWiki context.
     * @return the found wiki document references
     * @throws XWikiException error when getting pages names from database.
     * @since 2.2M2
     */
    public List<DocumentReference> getBackLinkedReferences(XWikiContext context) throws XWikiException
    {
        Set<EntityReference> references;
        try {
            references = getLinkStore().resolveBackLinkedEntities(getDocumentReference());
        } catch (LinkException e) {
            throw new XWikiException("Failed to load backlinks for reference [" + getDocumentReference() + "]", e);
        }

        Set<DocumentReference> documentReferences = new HashSet<>(references.size());
        for (EntityReference entityReference : references) {
            // Resolve the DOCUMENT reference
            DocumentReference linkReference = context.getWiki().getDocumentReference(entityReference, context);

            // Retro compatibility: remove the locale as it's what is expected of #getBackLinkedReferences(XWikicontext)
            if (linkReference.getLocale() != null) {
                linkReference = new DocumentReference(linkReference, (Locale) null);
            }

            // Add the reference
            documentReferences.add(linkReference);
        }

        return new ArrayList<>(documentReferences);
    }

    /**
     * @deprecated use {@link #getBackLinkedReferences(XWikiContext)}
     */
    @Deprecated(since = "2.2M2")
    public List<String> getBackLinkedPages(XWikiContext context) throws XWikiException
    {
        List<DocumentReference> references = getBackLinkedReferences(context);

        EntityReferenceSerializer<String> serializer = getCompactWikiEntityReferenceSerializer();

        List<String> documentNames = new ArrayList<>(references.size());
        for (DocumentReference reference : references) {
            // Serialize the reference
            documentNames.add(serializer.serialize(reference));
        }

        return documentNames;
    }

    /**
     * Get a list of unique links from this document to others documents.
     * <ul>
     * <li>xwiki/1.0 content: get the unique links associated to document from database. This is stored when the
     * document is saved. You can use "backlinks" in XWikiPreferences or "xwiki.backlinks" in xwiki.cfg file to enable
     * links storage in the database.</li>
     * <li>Other content: call {@link #getUniqueLinkedPages(XWikiContext)} and generate the List.</li>
     * </ul>
     *
     * @param context the XWiki context
     * @return the found wiki links.
     * @throws XWikiException error when getting links from database when xwiki/1.0 content.
     * @since 1.9M2
     */
    public Set<XWikiLink> getUniqueWikiLinkedPages(XWikiContext context) throws XWikiException
    {
        Set<XWikiLink> links;

        // We don't handle the links the same way in 1.0 syntax for retro-compatibility reason
        // So here we explicitly get the link from the DB instead of looking inside the document.
        if (is10Syntax()) {
            links = new LinkedHashSet<>(getStore(context).loadLinks(getId(), context, true));
        } else {
            Set<String> linkedPages = getUniqueLinkedPages(context);
            links = new LinkedHashSet<>(linkedPages.size());
            for (String linkedPage : linkedPages) {
                XWikiLink wikiLink = new XWikiLink();

                wikiLink.setDocId(getId());
                wikiLink.setFullName(LOCAL_REFERENCE_SERIALIZER.serialize(getDocumentReference()));
                wikiLink.setLink(linkedPage);

                links.add(wikiLink);
            }
        }

        return links;
    }

    /**
     * Extract all the unique static (i.e. not generated by macros) wiki links (pointing to wiki page) from this
     * xwiki/1.0 document's content to others documents.
     *
     * @param context the XWiki context.
     * @return the document references for linked pages, if null an error append.
     * @since 1.9M2
     */
    private Set<DocumentReference> getUniqueLinkedPages10(XWikiContext context)
    {
        Set<DocumentReference> pageNames;

        try {
            List<String> list = context.getUtil().getUniqueMatches(getContent(), "\\[(.*?)\\]", 1);
            pageNames = new HashSet<DocumentReference>(list.size());

            DocumentReference currentDocumentReference = getDocumentReference();
            for (String name : list) {
                int i1 = name.indexOf('>');
                if (i1 != -1) {
                    name = name.substring(i1 + 1);
                }
                i1 = name.indexOf("&gt;");
                if (i1 != -1) {
                    name = name.substring(i1 + 4);
                }
                i1 = name.indexOf('#');
                if (i1 != -1) {
                    name = name.substring(0, i1);
                }
                i1 = name.indexOf('?');
                if (i1 != -1) {
                    name = name.substring(0, i1);
                }

                // Let's get rid of anything that's not a real link
                if (name.trim().equals("") || (name.indexOf('$') != -1) || (name.indexOf("://") != -1)
                    || (name.indexOf('"') != -1) || (name.indexOf('\'') != -1) || (name.indexOf("..") != -1)
                    || (name.indexOf(':') != -1) || (name.indexOf('=') != -1)) {
                    continue;
                }

                // generate the link
                String newname = StringUtils.replace(Util.noaccents(name), " ", "");

                // If it is a local link let's add the space
                if (newname.indexOf('.') == -1) {
                    newname = getSpace() + "." + name;
                }
                if (context.getWiki().exists(newname, context)) {
                    name = newname;
                } else {
                    // If it is a local link let's add the space
                    if (name.indexOf('.') == -1) {
                        name = getSpace() + "." + name;
                    }
                }

                // If the reference is empty, the link is an autolink
                if (!StringUtils.isEmpty(name)) {
                    // The reference may not have the space or even document specified (in case of an empty
                    // string)
                    // Thus we need to find the fully qualified document name
                    DocumentReference documentReference = getCurrentDocumentReferenceResolver().resolve(name);

                    // Verify that the link is not an autolink (i.e. a link to the current document)
                    if (!documentReference.equals(currentDocumentReference)) {
                        pageNames.add(documentReference);
                    }
                }
            }

            return pageNames;
        } catch (Exception e) {
            // This should never happen
            LOGGER.error("Failed to get linked documents", e);

            return null;
        }
    }

    /**
     * Extract all the unique entity references found in the current document (they can be wiki links, macro parameters,
     * etc) and pointing to entities specified in the passed {@code entityTypes} map (the keys represent the entity
     * references that will be returned).
     *
     * @param context the XWiki context
     * @param entityTypes the mapping of the types of references to return (and their corresponding resource types)
     * @return the serialized entity references, and null if an error happened
     * @since 14.2RC1
     */
    private Set<EntityReference> getUniqueLinkedEntityReferences(XWikiContext context,
        Map<EntityType, Set<ResourceType>> entityTypes)
    {
        Set<EntityReference> references;

        XWikiDocument contextDoc = context.getDoc();
        WikiReference contextWikiReference = context.getWikiReference();

        try {
            // Make sure the right document is used as context document
            context.setDoc(this);
            // Make sure the right wiki is used as context document
            context.setWikiReference(getDocumentReference().getWikiReference());

            if (is10Syntax()) {
                references = (Set) getUniqueLinkedPages10(context);
            } else {
                references = new LinkedHashSet<>();

                // Document content
                XDOM dom = getXDOM();
                getUniqueLinkedEntityReferences(dom, entityTypes, references);

                // XObjects
                for (List<BaseObject> xobjects : getXObjects().values()) {
                    xobjects.stream()
                        .forEach(xobject -> getUniqueLinkedEntityReferences(xobject, entityTypes, references, context));
                }
            }
        } finally {
            context.setDoc(contextDoc);
            context.setWikiReference(contextWikiReference);
        }

        return references;
    }

    private void getUniqueLinkedEntityReferences(BaseObject xobject, Map<EntityType, Set<ResourceType>> entityTypes,
        Set<EntityReference> references, XWikiContext xcontext)
    {
        if (xobject == null) {
            return;
        }

        BaseClass xclass = xobject.getXClass(xcontext);

        for (Object fieldClass : xclass.getProperties()) {
            // Wiki content stored in xobjects
            if (fieldClass instanceof TextAreaClass && ((TextAreaClass) fieldClass).isWikiContent()) {
                TextAreaClass textAreaClass = (TextAreaClass) fieldClass;
                PropertyInterface field = xobject.getField(textAreaClass.getName());

                // Make sure the field is the right type (might happen while a document is being migrated)
                if (field instanceof LargeStringProperty) {
                    LargeStringProperty largeField = (LargeStringProperty) field;

                    try {
                        XDOM dom = parseContent(getSyntax(), largeField.getValue(), getDocumentReference());
                        getUniqueLinkedEntityReferences(dom, entityTypes, references);
                    } catch (XWikiException e) {
                        LOGGER.warn("Failed to extract links from xobject property [{}], skipping it. Error: {}",
                            largeField.getReference(), ExceptionUtils.getRootCauseMessage(e));
                    }
                }
            }
        }
    }

    private void getUniqueLinkedEntityReferences(XDOM dom, Map<EntityType, Set<ResourceType>> entityTypes,
        Set<EntityReference> references)
    {
        Set<EntityReference> uniqueLinkedEntityReferences =
            getLinkParser().getUniqueLinkedEntityReferences(dom, entityTypes, getDocumentReference());
        references.addAll(uniqueLinkedEntityReferences);
    }

    private Set<DocumentReference> toDocumentReferenceSet(Collection<? extends EntityReference> entityReferences,
        DocumentReference baseReference)
    {
        Set<DocumentReference> documentReferences = new LinkedHashSet<>(entityReferences.size());

        for (EntityReference entityRefefence : entityReferences) {
            documentReferences.add(getCurrentReferenceDocumentReferenceResolver().resolve(entityRefefence,
                EntityType.DOCUMENT, baseReference));
        }

        return documentReferences;
    }

    /**
     * Extract all the unique entity references found in the current document (they can be wiki links, macro parameters,
     * etc) and pointing to documents (either using a Document Reference or a Page Reference).
     *
     * @param context the XWiki context
     * @return the serialized entity references, and null if an error happened
     * @since 1.9M2
     */
    public Set<String> getUniqueLinkedPages(XWikiContext context)
    {
        // Only return document references.
        Set<EntityReference> references = getUniqueLinkedEntityReferences(context,
            Map.of(EntityType.DOCUMENT, Set.of(ResourceType.SPACE, ResourceType.DOCUMENT, ResourceType.ATTACHMENT),
                EntityType.PAGE, Set.of(ResourceType.PAGE, ResourceType.PAGE_ATTACHMENT)));
        Set<String> documentNames = new LinkedHashSet<>(references.size());

        XWikiDocument contextDoc = context.getDoc();
        String contextWiki = context.getWikiId();
        EntityReferenceSerializer<String> serializer;

        try {
            // Specify the right context information for using the compact wiki serializer properly
            // Make sure the right document is used as context document
            context.setDoc(this);
            // Make sure the right wiki is used as context document
            context.setWikiId(getDocumentReference().getWikiReference().getName());

            // for retro-compatibility reason we don't use the same serializer for 1.0 syntax.
            if (is10Syntax()) {
                serializer = getCompactEntityReferenceSerializer();
            } else {
                serializer = getCompactWikiEntityReferenceSerializer();
            }

            for (EntityReference reference : references) {
                // Get the reference of the document
                DocumentReference linkDocumentReference = context.getWiki().getDocumentReference(reference, context);

                // Serialize the reference
                documentNames.add(serializer.serialize(linkDocumentReference));
            }
        } finally {
            context.setDoc(contextDoc);
            context.setWikiId(contextWiki);
        }

        return documentNames;
    }

    /**
     * Extract all the unique static (i.e. not generated by macros) entity references pointed by wiki links (pointing to
     * wiki document or attachments) for the current context document.
     *
     * @param context the XWiki context
     * @return the entity references pointing to either document or attachments. If {@code null}, an error happened
     * @since 14.2RC1
     */
    public Set<EntityReference> getUniqueLinkedEntities(XWikiContext context)
    {
        // Return both document and attachment references.
        // Note that we return PAGE and PAGE_ATTACHMENT since it's not possible to convert them to DOCUMENT and
        // ATTACHMENT since there's no way of knowing (without querying the DB) if they point to a terminal page or
        // a non-terminal one (and thus the conversion would be wrong).
        return getUniqueLinkedEntityReferences(context,
            Map.of(EntityType.DOCUMENT, Set.of(ResourceType.SPACE, ResourceType.DOCUMENT), EntityType.PAGE,
                Set.of(ResourceType.PAGE), EntityType.ATTACHMENT, Set.of(ResourceType.ATTACHMENT),
                EntityType.PAGE_ATTACHMENT, Set.of(ResourceType.PAGE_ATTACHMENT)));
    }

    /**
     * Returns a list of references of all documents which list this document as their parent, in the current wiki.
     * {@link #getChildren(int, int, com.xpn.xwiki.XWikiContext)}
     *
     * @since 2.2M2
     */
    public List<DocumentReference> getChildrenReferences(XWikiContext context) throws XWikiException
    {
        return getChildrenReferences(0, 0, context);
    }

    /**
     * @deprecated use {@link #getChildrenReferences(XWikiContext)}
     */
    @Deprecated(since = "2.2M2")
    public List<String> getChildren(XWikiContext context) throws XWikiException
    {
        return getChildren(0, 0, context);
    }

    /**
     * Returns a list of references of all documents which list this document as their parent, in the wiki of current
     * document.
     *
     * @param nb The number of results to return.
     * @param start The number of results to skip before we begin returning results.
     * @param context The {@link com.xpn.xwiki.XWikiContext context}.
     * @return the list of document references
     * @throws XWikiException If there's an error querying the database.
     * @since 2.2M2
     */
    public List<DocumentReference> getChildrenReferences(int nb, int start, XWikiContext context) throws XWikiException
    {
        // Use cases:
        // - the parent document reference saved in the database matches the reference of this document, in its fully
        // serialized form (eg "wiki:space.page"). Note that this is normally not required since the wiki part
        // isn't saved in the database when it matches the current wiki.
        // - the parent document reference saved in the database matches the reference of this document, in its
        // serialized form without the wiki part (eg "space.page"). The reason we don't need to specify the wiki
        // part is because document parents saved in the database don't have the wiki part specified when it matches
        // the current wiki.
        // - the parent document reference saved in the database matches the page name part of this document's
        // reference (eg "page") and the parent document's space is the same as this document's space.
        List<DocumentReference> children = new ArrayList<DocumentReference>();

        try {
            WikiReference wikiReference = this.getDocumentReference().getWikiReference();
            Query query = getStore().getQueryManager()
                .createQuery("select distinct doc.fullName from XWikiDocument doc where "
                    + "doc.parent=:prefixedFullName or doc.parent=:fullName or (doc.parent=:name and doc.space=:space)",
                    Query.XWQL);
            query.addFilter(Utils.getComponent(QueryFilter.class, "hidden"));
            query.bindValue("prefixedFullName",
                getDefaultEntityReferenceSerializer().serialize(getDocumentReference()));
            query.bindValue("fullName", LOCAL_REFERENCE_SERIALIZER.serialize(getDocumentReference()));
            query.bindValue("name", getDocumentReference().getName());
            query.bindValue("space",
                LOCAL_REFERENCE_SERIALIZER.serialize(getDocumentReference().getLastSpaceReference()));
            query.setLimit(nb).setOffset(start).setWiki(wikiReference.getName());

            List<String> queryResults = query.execute();

            for (String fullName : queryResults) {
                children.add(getCurrentDocumentReferenceResolver().resolve(fullName, wikiReference));
            }
        } catch (QueryException e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_STORE, XWikiException.ERROR_XWIKI_UNKNOWN,
                String.format("Failed to retrieve children for document [%s]", this.getDocumentReference()), e);
        }

        return children;
    }

    /**
     * @deprecated use {@link #getChildrenReferences(XWikiContext)}
     */
    @Deprecated(since = "2.2M2")
    public List<String> getChildren(int nb, int start, XWikiContext context) throws XWikiException
    {
        List<String> childrenNames = new ArrayList<String>();
        for (DocumentReference reference : getChildrenReferences(nb, start, context)) {
            childrenNames.add(LOCAL_REFERENCE_SERIALIZER.serialize(reference));
        }
        return childrenNames;
    }

    /**
     * @since 2.2M2
     */
    public void renameProperties(DocumentReference classReference, Map<String, String> fieldsToRename)
    {
        List<BaseObject> objects = this.xObjects.get(classReference);
        if (objects == null) {
            return;
        }

        boolean isDirty = false;
        for (BaseObject bobject : objects) {
            if (bobject == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : fieldsToRename.entrySet()) {
                String origname = entry.getKey();
                String newname = entry.getValue();
                BaseProperty origprop = (BaseProperty) bobject.safeget(origname);
                if (origprop != null) {
                    BaseProperty prop = origprop.clone();
                    bobject.removeField(origname);
                    prop.setName(newname);
                    bobject.addField(newname, prop);

                    isDirty = true;
                }
            }
        }

        // If at least one property was renamed, mark the document dirty.
        if (isDirty) {
            setMetaDataDirty(true);
        }
    }

    /**
     * @deprecated use {@link #renameProperties(DocumentReference, Map)} instead
     */
    @Deprecated(since = "2.2M2")
    public void renameProperties(String className, Map<String, String> fieldsToRename)
    {
        renameProperties(resolveClassReference(className), fieldsToRename);
    }

    /**
     * @since 2.2M1
     */
    public void addXObjectToRemove(BaseObject object)
    {
        getXObjectsToRemove().add(object);
        object.setOwnerDocument(null);
        setMetaDataDirty(true);
    }

    /**
     * Automatically add objects present in the old version, but not in the current document, to the list of objects
     * marked for removal from the database.
     *
     * @param previousVersion the version of the document present in the database
     * @since 3.3M2
     */
    public void addXObjectsToRemoveFromVersion(XWikiDocument previousVersion)
    {
        if (previousVersion == null) {
            return;
        }
        for (List<BaseObject> objects : previousVersion.getXObjects().values()) {
            for (BaseObject originalObj : objects) {
                if (originalObj != null) {
                    BaseObject newObj = getXObject(originalObj.getXClassReference(), originalObj.getNumber());
                    if (newObj == null) {
                        // The object was deleted.
                        this.addXObjectToRemove(originalObj);
                    }
                }
            }
        }
    }

    /**
     * @deprecated use {@link #addXObjectToRemove(BaseObject)} )} instead
     */
    @Deprecated(since = "2.2M2")
    public void addObjectsToRemove(BaseObject object)
    {
        addXObjectToRemove(object);
    }

    /**
     * @since 2.2M2
     */
    public List<BaseObject> getXObjectsToRemove()
    {
        return this.xObjectsToRemove;
    }

    /**
     * @deprecated use {@link #getObjectsToRemove()} instead
     */
    @Deprecated(since = "2.2M2")
    public ArrayList<BaseObject> getObjectsToRemove()
    {
        return (ArrayList<BaseObject>) getXObjectsToRemove();
    }

    /**
     * @since 2.2M1
     */
    public void setXObjectsToRemove(List<BaseObject> objectsToRemove)
    {
        this.xObjectsToRemove = objectsToRemove;
        setMetaDataDirty(true);
    }

    public List<String> getIncludedPages(XWikiContext context)
    {
        try {
            return getIncludedPagesInternal(context);
        } catch (Exception e) {
            // If an error happens then we return an empty list of included pages. We don't want to fail by throwing an
            // exception since it'll lead to several errors in the UI (such as in the Information Panel in edit mode).
            LOGGER.error("Failed to get included pages for [{}]", getDocumentReference(), e);
            return Collections.emptyList();
        }
    }

    private List<String> getIncludedPagesInternal(XWikiContext context)
    {
        if (is10Syntax()) {
            return getIncludedPagesForXWiki10Syntax(getContent(), context);
        } else {
            // Find all include macros listed on the page
            XDOM dom = getXDOM();

            List<String> result = new ArrayList<String>();
            List<MacroBlock> macroBlocks =
                dom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
            for (MacroBlock macroBlock : macroBlocks) {
                // - Add each document pointed to by the include macro
                // - Also add all the included pages found in the velocity macro when using the deprecated #include*
                // macros
                // This should be removed when we fully drop support for the XWiki Syntax 1.0 but for now we want to
                // play nice with people migrating from 1.0 to 2.0 syntax

                if (macroBlock.getId().equalsIgnoreCase("include") || macroBlock.getId().equalsIgnoreCase("display")) {
                    String documentName = macroBlock.getParameters().get("reference");
                    if (StringUtils.isEmpty(documentName)) {
                        documentName = macroBlock.getParameters().get("document");
                        if (StringUtils.isEmpty(documentName)) {
                            continue;
                        }
                    }

                    DocumentReference documentReference =
                        getExplicitDocumentReferenceResolver().resolve(documentName, getDocumentReference());
                    if (this.getDocumentReference().equals(documentReference)) {
                        // Skip auto-includes since they are not allowed anyway.
                        continue;
                    }

                    documentName = LOCAL_REFERENCE_SERIALIZER.serialize(documentReference);

                    result.add(documentName);
                } else if (macroBlock.getId().equalsIgnoreCase("velocity")
                    && !StringUtils.isEmpty(macroBlock.getContent())) {
                    // Try to find matching content inside each velocity macro
                    result.addAll(getIncludedPagesForXWiki10Syntax(macroBlock.getContent(), context));
                }
            }

            return result;
        }
    }

    private List<String> getIncludedPagesForXWiki10Syntax(String content, XWikiContext context)
    {
        try {
            String pattern = "#include(Topic|InContext|Form|Macros|parseGroovyFromPage)\\([\"'](.*?)[\"']\\)";
            List<String> list = context.getUtil().getUniqueMatches(content, pattern, 2);
            for (int i = 0; i < list.size(); i++) {
                String name = list.get(i);
                if (name.indexOf('.') == -1) {
                    list.set(i, getSpace() + "." + name);
                }
            }

            return list;
        } catch (Exception e) {
            LOGGER.error("Failed to extract include target from provided content [" + content + "]", e);

            return null;
        }
    }

    public List<String> getIncludedMacros(XWikiContext context)
    {
        return context.getWiki().getIncludedMacros(getSpace(), getContent(), context);
    }

    public String displayRendered(PropertyClass pclass, String prefix, BaseCollection object, XWikiContext context)
        throws XWikiException
    {
        String result = pclass.displayView(pclass.getName(), prefix, object, context);
        return getRenderedContent(result, Syntax.XWIKI_1_0.toIdString(), context);
    }

    public String displayView(PropertyClass pclass, String prefix, BaseCollection object, XWikiContext context)
    {
        return (pclass == null) ? "" : pclass.displayView(pclass.getName(), prefix, object, context);
    }

    public String displayEdit(PropertyClass pclass, String prefix, BaseCollection object, XWikiContext context)
    {
        return (pclass == null) ? "" : pclass.displayEdit(pclass.getName(), prefix, object, context);
    }

    public String displayHidden(PropertyClass pclass, String prefix, BaseCollection object, XWikiContext context)
    {
        return (pclass == null) ? "" : pclass.displayHidden(pclass.getName(), prefix, object, context);
    }

    /**
     * Return the first attachment of the attachment list either exactly matching the provided filename or that matches
     * the provided filename with a n arbitrary extension (i.e., filename.ext). To get only attachments that exactly
     * matches the provided filename use {@link #getExactAttachment(String)}.
     * 
     * @param filename the file name of the attachment with or without the extension
     * @return the {@link XWikiAttachment} corresponding to the file name, null if none can be found
     * @see #getExactAttachment(String)
     */
    public XWikiAttachment getAttachment(String filename)
    {
        XWikiAttachment output = this.attachmentList.getByFilename(filename);
        if (output == null && filename != null) {
            for (XWikiAttachment attach : getAttachmentList()) {
                if (attach.getFilename().startsWith(filename + ".")) {
                    output = attach;
                    break;
                }
            }
        }
        return output;
    }

    /**
     * Return the attachment that exactly matches the provided file. To also get the first attachment that matches the
     * provided filename with an arbitrary extension use {@link #getAttachment(String)}.
     *
     * @param filename the file name of the attachment
     * @return the {@link XWikiAttachment} exactly corresponding to the file name, null if none can be found
     * @since 14.1RC1
     * @since 13.10.3
     */
    public XWikiAttachment getExactAttachment(String filename)
    {
        return this.attachmentList.getByFilename(filename);
    }

    /**
     * Add passed attachment to the document.
     *
     * @param attachment the attachment to add
     * @since 5.3M2
     * @deprecated use {@link #setAttachment(XWikiAttachment)} instead
     */
    @Deprecated(since = "9.10RC1")
    public void addAttachment(XWikiAttachment attachment)
    {
        setAttachment(attachment);
    }

    /**
     * Insert passed attachment in the document and return any pre-existing attachment with the same name.
     * 
     * @param attachment the attachment to insert in the document
     * @return the attachment replaced by the passed attachment
     * @since 9.10RC1
     */
    public XWikiAttachment setAttachment(XWikiAttachment attachment)
    {
        return this.attachmentList.set(attachment);
    }

    /**
     * @deprecated use {@link #setAttachment(String, InputStream, XWikiContext)} instead
     */
    @Deprecated
    public XWikiAttachment addAttachment(String fileName, byte[] content, XWikiContext context) throws XWikiException
    {
        try {
            return setAttachment(fileName, new ByteArrayInputStream(content != null ? content : new byte[0]), context);
        } catch (IOException e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_DOC, XWikiException.ERROR_XWIKI_UNKNOWN,
                "Failed to set Attachment content", e);
        }
    }

    /**
     * @param fileName the name of the attachment
     * @param content the content of the attachment
     * @param context the XWiki context
     * @return the new attachment
     * @throws XWikiException never sent
     * @throws IOException when failing to read the passed content
     * @deprecated use {@link #setAttachment(String, InputStream, XWikiContext)} instead
     */
    @Deprecated(since = "9.10RC1")
    public XWikiAttachment addAttachment(String fileName, InputStream content, XWikiContext context)
        throws XWikiException, IOException
    {
        return setAttachment(fileName, content, context);
    }

    /**
     * Create or update attachment with the passed name with the passed content.
     * 
     * @param fileName the name of the attachment
     * @param content the content of the attachment
     * @param context the XWiki context
     * @return the new attachment
     * @throws IOException when failing to read the passed content
     * @since 9.10rc1
     */
    public XWikiAttachment setAttachment(String fileName, InputStream content, XWikiContext context) throws IOException
    {
        int i = fileName.indexOf('\\');
        if (i == -1) {
            i = fileName.indexOf('/');
        }

        String filename = fileName.substring(i + 1);

        XWikiAttachment attachment = getExactAttachment(filename);
        if (attachment == null) {
            attachment = new XWikiAttachment(this, filename);

            // Add the attachment in the current doc
            setAttachment(attachment);
        }

        attachment.setContent(content);
        attachment.setAuthorReference(context.getUserReference());

        return attachment;
    }

    public BaseObject getFirstObject(String fieldname)
    {
        // Keeping this function with context null for compatibility reasons.
        // It should not be used, since it would miss properties which are only defined in the class
        // and not present in the object because the object was not updated
        return getFirstObject(fieldname, null);
    }

    public BaseObject getFirstObject(String fieldname, XWikiContext context)
    {
        Collection<List<BaseObject>> objectscoll = getXObjects().values();
        if (objectscoll == null) {
            return null;
        }

        for (List<BaseObject> objects : objectscoll) {
            for (BaseObject obj : objects) {
                if (obj != null) {
                    BaseClass bclass = obj.getXClass(context);
                    if (bclass != null) {
                        Set<String> set = bclass.getPropertyList();
                        if ((set != null) && set.contains(fieldname)) {
                            return obj;
                        }
                    }
                    Set<String> set = obj.getPropertyList();
                    if ((set != null) && set.contains(fieldname)) {
                        return obj;
                    }
                }
            }
        }

        return null;
    }

    /**
     * @since 2.2.3
     */
    public void setProperty(EntityReference classReference, String fieldName, BaseProperty value)
    {
        BaseObject bobject = prepareXObject(classReference);
        bobject.safeput(fieldName, value);
    }

    /**
     * @deprecated use {@link #setProperty(EntityReference, String, BaseProperty)} instead
     */
    @Deprecated(since = "2.2M2")
    public void setProperty(String className, String fieldName, BaseProperty value)
    {
        setProperty(getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()),
            fieldName, value);
    }

    /**
     * @since 2.2M2
     */
    public int getIntValue(DocumentReference classReference, String fieldName)
    {
        return getIntValue(classReference, fieldName, 0);
    }

    /**
     * Retrieve the int value of the given property of the first object of the given class.
     *
     * @param classReference the reference of the object to find
     * @param fieldName the property to get the value from
     * @param defaultValue the default value to return if the object doesn't exist, or if the property is not set
     * @return the retrieved value or the default value.
     * @since 11.9RC1
     */
    public int getIntValue(DocumentReference classReference, String fieldName, int defaultValue)
    {
        BaseObject obj = getXObject(classReference, 0);
        if (obj == null) {
            return defaultValue;
        }

        return obj.getIntValue(fieldName, defaultValue);
    }

    /**
     * @deprecated use {@link #getIntValue(DocumentReference, String)} instead
     */
    @Deprecated(since = "2.2M2")
    public int getIntValue(String className, String fieldName)
    {
        return getIntValue(resolveClassReference(className), fieldName);
    }

    /**
     * @since 2.2M2
     */
    public long getLongValue(DocumentReference classReference, String fieldName)
    {
        BaseObject obj = getXObject(classReference, 0);
        if (obj == null) {
            return 0;
        }

        return obj.getLongValue(fieldName);
    }

    /**
     * @deprecated use {@link #getLongValue(DocumentReference, String)} instead
     */
    @Deprecated(since = "2.2M2")
    public long getLongValue(String className, String fieldName)
    {
        return getLongValue(resolveClassReference(className), fieldName);
    }

    /**
     * @since 6.2M1
     */
    public String getStringValue(EntityReference classReference, String fieldName)
    {
        return getStringValue(resolveClassReference(classReference), fieldName);
    }

    /**
     * @since 2.2M2
     */
    public String getStringValue(DocumentReference classReference, String fieldName)
    {
        BaseObject obj = getXObject(classReference);
        if (obj == null) {
            return "";
        }

        String result = obj.getStringValue(fieldName);
        if (result.equals(" ")) {
            return "";
        } else {
            return result;
        }
    }

    /**
     * @deprecated use {@link #getStringValue(DocumentReference, String)} instead
     */
    @Deprecated(since = "2.2M2")
    public String getStringValue(String className, String fieldName)
    {
        return getStringValue(resolveClassReference(className), fieldName);
    }

    public int getIntValue(String fieldName)
    {
        BaseObject object = getFirstObject(fieldName, null);
        if (object == null) {
            return 0;
        } else {
            return object.getIntValue(fieldName);
        }
    }

    public long getLongValue(String fieldName)
    {
        BaseObject object = getFirstObject(fieldName, null);
        if (object == null) {
            return 0;
        } else {
            return object.getLongValue(fieldName);
        }
    }

    public String getStringValue(String fieldName)
    {
        BaseObject object = getFirstObject(fieldName, null);
        if (object == null) {
            return "";
        }

        String result = object.getStringValue(fieldName);
        if (result.equals(" ")) {
            return "";
        } else {
            return result;
        }
    }

    /**
     * @since 2.2.3
     */
    public void setStringValue(EntityReference classReference, String fieldName, String value)
    {
        BaseObject bobject = prepareXObject(classReference);
        bobject.setStringValue(fieldName, value);
    }

    /**
     * @deprecated use {@link #setStringValue(EntityReference, String, String)} instead
     */
    @Deprecated(since = "2.2M2")
    public void setStringValue(String className, String fieldName, String value)
    {
        setStringValue(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()),
            fieldName, value);
    }

    /**
     * @since 2.2M2
     */
    public List getListValue(DocumentReference classReference, String fieldName)
    {
        BaseObject obj = getXObject(classReference);
        if (obj == null) {
            return new ArrayList();
        }

        return obj.getListValue(fieldName);
    }

    /**
     * @deprecated use {@link #getListValue(DocumentReference, String)} instead
     */
    @Deprecated(since = "2.2M2")
    public List getListValue(String className, String fieldName)
    {
        return getListValue(resolveClassReference(className), fieldName);
    }

    public List getListValue(String fieldName)
    {
        BaseObject object = getFirstObject(fieldName, null);
        if (object == null) {
            return new ArrayList();
        }

        return object.getListValue(fieldName);
    }

    /**
     * @since 2.2.3
     */
    public void setStringListValue(EntityReference classReference, String fieldName, List value)
    {
        BaseObject bobject = prepareXObject(classReference);
        bobject.setStringListValue(fieldName, value);
    }

    /**
     * @deprecated use {@link #setStringListValue(EntityReference, String, List)} instead
     */
    @Deprecated(since = "2.2M2")
    public void setStringListValue(String className, String fieldName, List value)
    {
        setStringListValue(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()),
            fieldName, value);
    }

    /**
     * @since 2.2.3
     */
    public void setDBStringListValue(EntityReference classReference, String fieldName, List value)
    {
        BaseObject bobject = prepareXObject(classReference);
        bobject.setDBStringListValue(fieldName, value);
    }

    /**
     * @deprecated use {@link #setDBStringListValue(EntityReference, String, List)} instead
     */
    @Deprecated(since = "2.2M2")
    public void setDBStringListValue(String className, String fieldName, List value)
    {
        setDBStringListValue(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()),
            fieldName, value);
    }

    /**
     * @since 2.2.3
     */
    public void setLargeStringValue(EntityReference classReference, String fieldName, String value)
    {
        BaseObject bobject = prepareXObject(classReference);
        bobject.setLargeStringValue(fieldName, value);
    }

    /**
     * @deprecated use {@link #setLargeStringValue(EntityReference, String, String)} instead
     */
    @Deprecated(since = "2.2M2")
    public void setLargeStringValue(String className, String fieldName, String value)
    {
        setLargeStringValue(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()),
            fieldName, value);
    }

    /**
     * @since 2.2.3
     */
    public void setIntValue(EntityReference classReference, String fieldName, int value)
    {
        BaseObject bobject = prepareXObject(classReference);
        bobject.setIntValue(fieldName, value);
    }

    /**
     * @deprecated use {@link #setIntValue(EntityReference, String, int)} instead
     */
    @Deprecated(since = "2.2M2")
    public void setIntValue(String className, String fieldName, int value)
    {
        setIntValue(getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()),
            fieldName, value);
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated use {@link #getDocumentReference()} instead
     */
    @Deprecated(since = "2.2M1")
    public String getDatabase()
    {
        return getDocumentReference().getWikiReference().getName();
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for loading a XWikiDocument.
     *
     * @deprecated use {@link #setDocumentReference(DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public void setDatabase(String database)
    {
        if (database != null) {
            DocumentReference reference = getDocumentReference();
            WikiReference wiki = reference.getWikiReference();
            WikiReference newWiki = new WikiReference(database);
            if (!newWiki.equals(wiki)) {
                setDocumentReferenceInternal(reference.replaceParent(wiki, newWiki));
            }
        }
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated use {@link #getLocale()} instead
     */
    @Deprecated(since = "4.3M2")
    public String getLanguage()
    {
        return getLocale().toString();
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated use {@link #setLocale(Locale)} instead
     */
    @Deprecated(since = "4.3M2")
    public void setLanguage(String language)
    {
        setLocale(LocaleUtils.toLocale(Util.normalizeLanguage(language), Locale.ROOT));
    }

    /**
     * @return the locale of the document
     */
    public Locale getLocale()
    {
        return this.locale != null ? this.locale : Locale.ROOT;
    }

    /**
     * @param locale the locale of the document
     */
    public void setLocale(Locale locale)
    {
        if (!Objects.equals(this.locale, locale)) {
            this.locale = locale;

            setMetaDataDirty(true);

            // Clean various caches

            this.keyCache = null;
            this.localKeyCache = null;
            this.documentReferenceWithLocaleCache = null;
            this.pageReferenceWithLocaleCache = null;
        }
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated use {@link #getDefaultLocale()} instead
     */
    @Deprecated(since = "4.3M2")
    public String getDefaultLanguage()
    {
        return getDefaultLocale().toString();
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @deprecated use {@link #setDefaultLocale(Locale)} instead
     */
    @Deprecated(since = "4.3M2")
    public void setDefaultLanguage(String defaultLanguage)
    {
        setDefaultLocale(LocaleUtils.toLocale(defaultLanguage, Locale.ROOT));
    }

    public Locale getDefaultLocale()
    {
        return this.defaultLocale != null ? this.defaultLocale : Locale.ROOT;
    }

    public void setDefaultLocale(Locale defaultLocale)
    {
        if (!Objects.equals(this.defaultLocale, defaultLocale)) {
            this.defaultLocale = defaultLocale;

            setMetaDataDirty(true);
        }
    }

    public int getTranslation()
    {
        return getLocale().equals(Locale.ROOT) ? 0 : 1;
    }

    /**
     * Note that this method cannot be removed for now since it's called by Hibernate when loading a XWikiDocument.
     *
     * @deprecated stored in the database to speedup some queries (really ?) but in {@link XWikiDocument} it's
     *             calculated based on the document locale
     */
    @Deprecated(since = "5.4.6")
    public void setTranslation(int translation)
    {
        // Do nothing
    }

    public String getTranslatedContent(XWikiContext context) throws XWikiException
    {
        String language = context.getWiki().getLanguagePreference(context);

        return getTranslatedContent(language, context);
    }

    public String getTranslatedContent(String locale, XWikiContext context) throws XWikiException
    {
        XWikiDocument tdoc = getTranslatedDocument(locale, context);
        return tdoc.getContent();
    }

    public XWikiDocument getTranslatedDocument(XWikiContext context) throws XWikiException
    {
        String locale = context.getWiki().getLanguagePreference(context);
        return getTranslatedDocument(locale, context);
    }

    /**
     * Return the document in the provided language.
     * <p>
     * This method return this if the provided language does not exist. See
     *
     * @param language the language of the document to return
     * @param context the XWiki Context
     * @return the document in the provided language or this if the provided language does not exist
     * @throws XWikiException error when loading the document
     * @deprecated use {@link #getTranslatedDocument(Locale, XWikiContext)} instead
     */
    @Deprecated(since = "4.3M2")
    public XWikiDocument getTranslatedDocument(String language, XWikiContext context) throws XWikiException
    {
        return getTranslatedDocument(LocaleUtils.toLocale(language, Locale.ROOT), context);
    }

    /**
     * Return the document in the provided language.
     * <p>
     * This method return this if the provided language does not exist. See
     *
     * @param locale the locale of the document to return
     * @param context the XWiki Context
     * @return the document in the provided language or this if the provided language does not exist
     * @throws XWikiException error when loading the document
     */
    public XWikiDocument getTranslatedDocument(Locale locale, XWikiContext context) throws XWikiException
    {
        XWikiDocument tdoc = this;

        if (locale != null && !locale.equals(Locale.ROOT) && !locale.equals(getDefaultLocale())) {
            try {
                tdoc = context.getWiki().getDocument(new DocumentReference(getDocumentReference(), locale), context);

                if (!tdoc.isNew()) {
                    return tdoc;
                }
            } catch (Exception e) {
                LOGGER.error("Error when loading document {} for locale {}", getDocumentReference(), locale, e);
            }

            tdoc = getTranslatedDocument(LocaleUtils.getParentLocale(locale), context);
        }

        return tdoc;
    }

    /**
     * @deprecated use {@link #getRealLocale()} instead
     */
    @Deprecated(since = "4.3M1")
    public String getRealLanguage(XWikiContext context) throws XWikiException
    {
        return getRealLanguage();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.xwiki.bridge.DocumentModelBridge#getRealLanguage()
     * @deprecated use {@link #getRealLocale()} instead
     */
    @Override
    @Deprecated(since = "4.3M1")
    public String getRealLanguage()
    {
        String lang = getLanguage();
        if (lang.equals("")) {
            return getDefaultLanguage();
        } else {
            return lang;
        }
    }

    /**
     * @return the actual locale of the document
     */
    public Locale getRealLocale()
    {
        Locale locale = getLocale();
        if (locale.equals(Locale.ROOT)) {
            locale = getDefaultLocale();
        }

        return locale;
    }

    /**
     * @deprecated use {@link #getTranslationLocales(XWikiContext)} instead
     */
    @Deprecated(since = "5.1M2")
    public List<String> getTranslationList(XWikiContext context) throws XWikiException
    {
        // in few cases like accessing a deleted document, the store might be null.
        if (getStore() != null) {
            return getStore().getTranslationList(this, context);
        } else {
            return Collections.emptyList();
        }

    }

    /**
     * The locales of the translation of this document (the default locale is not included).
     *
     * @param context the XWiki context
     * @return the locales of the translations
     * @throws XWikiException if retriving the translations from the database failed
     */
    public List<Locale> getTranslationLocales(XWikiContext context) throws XWikiException
    {
        List<String> translations = getTranslationList(context);

        List<Locale> locales = new ArrayList<Locale>(translations.size());
        for (String translationString : translations) {
            locales.add(LocaleUtils.toLocale(translationString));
        }

        return locales;
    }

    public List<Delta> getXMLDiff(XWikiDocument fromDoc, XWikiDocument toDoc, XWikiContext context)
        throws XWikiException, DifferentiationFailedException
    {
        return getDeltas(
            Diff.diff(ToString.stringToArray(fromDoc.toXML(context)), ToString.stringToArray(toDoc.toXML(context))));
    }

    public List<Delta> getContentDiff(XWikiDocument fromDoc, XWikiDocument toDoc, XWikiContext context)
        throws XWikiException, DifferentiationFailedException
    {
        return getDeltas(
            Diff.diff(ToString.stringToArray(fromDoc.getContent()), ToString.stringToArray(toDoc.getContent())));
    }

    public List<Delta> getContentDiff(String fromRev, String toRev, XWikiContext context)
        throws XWikiException, DifferentiationFailedException
    {
        XWikiDocument fromDoc = context.getWiki().getDocument(this, fromRev, context);
        XWikiDocument toDoc = context.getWiki().getDocument(this, toRev, context);
        if (fromDoc == null) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_DIFF, XWikiException.ERROR_XWIKI_DIFF_CONTENT_ERROR,
                String.format("The revision [%s] cannot be found in [%s] for making diff.", fromRev,
                    this.getDocumentReference()));
        }
        if (toRev == null) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_DIFF, XWikiException.ERROR_XWIKI_DIFF_CONTENT_ERROR,
                String.format("The revision [%s] cannot be found in [%s] for making diff.", toRev,
                    this.getDocumentReference()));
        }
        return getContentDiff(fromDoc, toDoc, context);
    }

    public List<Delta> getContentDiff(String fromRev, XWikiContext context)
        throws XWikiException, DifferentiationFailedException
    {
        XWikiDocument revdoc = context.getWiki().getDocument(this, fromRev, context);
        return getContentDiff(revdoc, this, context);
    }

    public List<Delta> getLastChanges(XWikiContext context) throws XWikiException, DifferentiationFailedException
    {
        Version version = getRCSVersion();
        try {
            String prev = getDocumentArchive(context).getPrevVersion(version).toString();
            XWikiDocument prevDoc = context.getWiki().getDocument(this, prev, context);

            return getDeltas(
                Diff.diff(ToString.stringToArray(prevDoc.getContent()), ToString.stringToArray(getContent())));
        } catch (Exception ex) {
            LOGGER.debug("Exception getting differences from previous version", ex);
        }

        return new ArrayList<Delta>();
    }

    public List<Delta> getRenderedContentDiff(XWikiDocument fromDoc, XWikiDocument toDoc, XWikiContext context)
        throws XWikiException, DifferentiationFailedException
    {
        String originalContent = fromDoc.getRenderedContent(context);
        String newContent = toDoc.getRenderedContent(context);

        return getDeltas(Diff.diff(ToString.stringToArray(originalContent), ToString.stringToArray(newContent)));
    }

    public List<Delta> getRenderedContentDiff(String fromRev, String toRev, XWikiContext context)
        throws XWikiException, DifferentiationFailedException
    {
        XWikiDocument fromDoc = context.getWiki().getDocument(this, fromRev, context);
        XWikiDocument toDoc = context.getWiki().getDocument(this, toRev, context);

        return getRenderedContentDiff(fromDoc, toDoc, context);
    }

    public List<Delta> getRenderedContentDiff(String fromRev, XWikiContext context)
        throws XWikiException, DifferentiationFailedException
    {
        XWikiDocument revdoc = context.getWiki().getDocument(this, fromRev, context);

        return getRenderedContentDiff(revdoc, this, context);
    }

    protected List<Delta> getDeltas(Revision rev)
    {
        List<Delta> list = new ArrayList<Delta>();
        for (int i = 0; i < rev.size(); i++) {
            list.add(rev.getDelta(i));
        }

        return list;
    }

    public List<MetaDataDiff> getMetaDataDiff(String fromRev, String toRev, XWikiContext context) throws XWikiException
    {
        XWikiDocument fromDoc = context.getWiki().getDocument(this, fromRev, context);
        XWikiDocument toDoc = context.getWiki().getDocument(this, toRev, context);

        return getMetaDataDiff(fromDoc, toDoc, context);
    }

    public List<MetaDataDiff> getMetaDataDiff(String fromRev, XWikiContext context) throws XWikiException
    {
        XWikiDocument revdoc = context.getWiki().getDocument(this, fromRev, context);

        return getMetaDataDiff(revdoc, this, context);
    }

    public List<MetaDataDiff> getMetaDataDiff(XWikiDocument fromDoc, XWikiDocument toDoc, XWikiContext context)
        throws XWikiException
    {
        List<MetaDataDiff> list = new ArrayList<MetaDataDiff>();

        if (fromDoc == null || toDoc == null) {
            return list;
        }

        if (!fromDoc.getTitle().equals(toDoc.getTitle())) {
            list.add(new MetaDataDiff("title", fromDoc.getTitle(), toDoc.getTitle()));
        }

        if (ObjectUtils.notEqual(fromDoc.getRelativeParentReference(), toDoc.getRelativeParentReference())) {
            list.add(new MetaDataDiff("parent", fromDoc.getParent(), toDoc.getParent()));
        }

        UserReference fromDocOriginalAuthor = fromDoc.getAuthors().getOriginalMetadataAuthor();
        UserReference toDocOriginalAuthor = toDoc.getAuthors().getOriginalMetadataAuthor();
        if (ObjectUtils.notEqual(fromDocOriginalAuthor, toDocOriginalAuthor)) {
            list.add(new MetaDataDiff("author", userReferenceToString(fromDocOriginalAuthor),
                userReferenceToString(toDocOriginalAuthor)));
        }

        if (ObjectUtils.notEqual(fromDoc.getDocumentReference(), toDoc.getDocumentReference())) {
            list.add(new MetaDataDiff("reference", fromDoc.getDocumentReference(), toDoc.getDocumentReference()));
        }

        if (!fromDoc.getSpace().equals(toDoc.getSpace())) {
            list.add(new MetaDataDiff("web", fromDoc.getSpace(), toDoc.getSpace()));
        }

        if (!fromDoc.getName().equals(toDoc.getName())) {
            list.add(new MetaDataDiff("name", fromDoc.getName(), toDoc.getName()));
        }

        if (ObjectUtils.notEqual(fromDoc.getLocale(), toDoc.getLocale())) {
            list.add(new MetaDataDiff("language", fromDoc.getLanguage(), toDoc.getLanguage()));
        }

        if (ObjectUtils.notEqual(fromDoc.getDefaultLocale(), toDoc.getDefaultLocale())) {
            list.add(new MetaDataDiff("defaultLanguage", fromDoc.getDefaultLanguage(), toDoc.getDefaultLanguage()));
        }

        if (ObjectUtils.notEqual(fromDoc.getSyntax(), toDoc.getSyntax())) {
            list.add(new MetaDataDiff("syntax", fromDoc.getSyntax(), toDoc.getSyntax()));
        }

        if (fromDoc.isHidden() != toDoc.isHidden()) {
            list.add(new MetaDataDiff("hidden", fromDoc.isHidden(), toDoc.isHidden()));
        }

        if (fromDoc.isEnforceRequiredRights() != toDoc.isEnforceRequiredRights()) {
            list.add(new MetaDataDiff("enforceRequiredRights", fromDoc.isEnforceRequiredRights(),
                toDoc.isEnforceRequiredRights()));
        }

        return list;
    }

    public List<List<ObjectDiff>> getObjectDiff(String fromRev, String toRev, XWikiContext context)
        throws XWikiException
    {
        XWikiDocument fromDoc = context.getWiki().getDocument(this, fromRev, context);
        XWikiDocument toDoc = context.getWiki().getDocument(this, toRev, context);

        return getObjectDiff(fromDoc, toDoc, context);
    }

    public List<List<ObjectDiff>> getObjectDiff(String fromRev, XWikiContext context) throws XWikiException
    {
        XWikiDocument revdoc = context.getWiki().getDocument(this, fromRev, context);

        return getObjectDiff(revdoc, this, context);
    }

    /**
     * Return the object differences between two document versions. There is no hard requirement on the order of the two
     * versions, but the results are semantically correct only if the two versions are given in the right order.
     *
     * @param fromDoc The old ('before') version of the document.
     * @param toDoc The new ('after') version of the document.
     * @param context The {@link com.xpn.xwiki.XWikiContext context}.
     * @return The object differences. The returned list's elements are other lists, one for each changed object. The
     *         inner lists contain {@link ObjectDiff} elements, one object for each changed property of the object.
     *         Additionally, if the object was added or removed, then the first entry in the list will be an
     *         "object-added" or "object-removed" marker.
     */
    public List<List<ObjectDiff>> getObjectDiff(XWikiDocument fromDoc, XWikiDocument toDoc, XWikiContext context)
    {
        List<List<ObjectDiff>> difflist = new ArrayList<List<ObjectDiff>>();

        // Since objects could have been deleted or added, we iterate on both the old and the new
        // object collections.
        // First, iterate over the old objects.
        for (List<BaseObject> objects : fromDoc.getXObjects().values()) {
            for (BaseObject originalObj : objects) {
                // This happens when objects are deleted, and the document is still in the cache
                // storage.
                if (originalObj != null) {
                    BaseObject newObj = toDoc.getXObject(originalObj.getXClassReference(), originalObj.getNumber());
                    List<ObjectDiff> dlist;
                    if (newObj == null) {
                        // The object was deleted.
                        newObj = new BaseObject();
                        // We want the xclass reference to be set so that it can resolve the xclass properties.
                        newObj.setXClassReference(originalObj.getXClassReference());
                        dlist = newObj.getDiff(originalObj, context);
                        ObjectDiff deleteMarker =
                            new ObjectDiff(originalObj.getXClassReference(), originalObj.getNumber(),
                                originalObj.getGuid(), ObjectDiff.ACTION_OBJECTREMOVED, "", "", "", "");
                        dlist.add(0, deleteMarker);
                    } else {
                        // The object exists in both versions, but might have been changed.
                        dlist = newObj.getDiff(originalObj, context);
                    }
                    if (!dlist.isEmpty()) {
                        difflist.add(dlist);
                    }
                }
            }
        }

        // Second, iterate over the objects which are only in the new version.
        for (List<BaseObject> objects : toDoc.getXObjects().values()) {
            for (BaseObject newObj : objects) {
                // This happens when objects are deleted, and the document is still in the cache
                // storage.
                if (newObj != null) {
                    BaseObject originalObj = fromDoc.getXObject(newObj.getXClassReference(), newObj.getNumber());
                    if (originalObj == null) {
                        // TODO: Refactor this so that getDiff() accepts null Object as input.
                        // Only consider added objects, the other case was treated above.
                        originalObj = new BaseObject();
                        originalObj.setXClassReference(newObj.getRelativeXClassReference());
                        originalObj.setNumber(newObj.getNumber());
                        originalObj.setGuid(newObj.getGuid());
                        List<ObjectDiff> dlist = newObj.getDiff(originalObj, context);
                        ObjectDiff addMarker = new ObjectDiff(newObj.getXClassReference(), newObj.getNumber(),
                            newObj.getGuid(), ObjectDiff.ACTION_OBJECTADDED, "", "", "", "");
                        dlist.add(0, addMarker);
                        if (!dlist.isEmpty()) {
                            difflist.add(dlist);
                        }
                    }
                }
            }
        }

        return difflist;
    }

    public List<List<ObjectDiff>> getClassDiff(XWikiDocument fromDoc, XWikiDocument toDoc, XWikiContext context)
    {
        List<List<ObjectDiff>> difflist = new ArrayList<List<ObjectDiff>>();
        BaseClass oldClass = fromDoc.getXClass();
        BaseClass newClass = toDoc.getXClass();

        if ((newClass == null) && (oldClass == null)) {
            return difflist;
        }

        List<ObjectDiff> dlist = newClass.getDiff(oldClass, context);
        if (!dlist.isEmpty()) {
            difflist.add(dlist);
        }

        return difflist;
    }

    /**
     * @param fromDoc
     * @param toDoc
     * @param context
     * @return
     */
    public List<AttachmentDiff> getAttachmentDiff(XWikiDocument fromDoc, XWikiDocument toDoc, XWikiContext context)
    {
        List<AttachmentDiff> difflist = new ArrayList<AttachmentDiff>();
        for (XWikiAttachment origAttach : fromDoc.getAttachmentList()) {
            String fileName = origAttach.getFilename();
            XWikiAttachment newAttach = toDoc.getAttachment(fileName);
            origAttach = retrieveDeletedAttachment(fromDoc, origAttach, context);
            if (newAttach == null) {
                difflist.add(new AttachmentDiff(fileName, org.xwiki.diff.Delta.Type.DELETE, origAttach, newAttach));
            } else {
                newAttach = retrieveDeletedAttachment(toDoc, newAttach, context);
                try {
                    if (!origAttach.equalsData(newAttach, context)) {
                        difflist
                            .add(new AttachmentDiff(fileName, org.xwiki.diff.Delta.Type.CHANGE, origAttach, newAttach));
                    }
                } catch (XWikiException e) {
                    LOGGER.error("Failed to compare attachments [{}] and [{}]", origAttach.getReference(),
                        newAttach.getReference(), e);
                }
            }
        }

        for (XWikiAttachment newAttach : toDoc.getAttachmentList()) {
            String fileName = newAttach.getFilename();
            XWikiAttachment origAttach = fromDoc.getAttachment(fileName);
            newAttach = retrieveDeletedAttachment(toDoc, newAttach, context);
            if (origAttach == null) {
                difflist.add(new AttachmentDiff(fileName, org.xwiki.diff.Delta.Type.INSERT, origAttach, newAttach));
            }
        }

        return difflist;
    }

    private XWikiAttachment retrieveDeletedAttachment(XWikiDocument doc, XWikiAttachment attachment,
        XWikiContext context)
    {
        XWikiAttachment result = null;

        InputStream is = null;
        try {
            is = attachment.getContentInputStream(context);
            if (is == null) {
                AttachmentRecycleBinStore attachmentRecycleBinStore = context.getWiki().getAttachmentRecycleBinStore();
                List<DeletedAttachment> allDeletedAttachments =
                    attachmentRecycleBinStore.getAllDeletedAttachments(doc, context, true);

                for (DeletedAttachment deletedAttachment : allDeletedAttachments) {
                    XWikiAttachment restoredAttachment = deletedAttachment.restoreAttachment();
                    if (restoredAttachment.getDate().before(attachment.getDate())) {
                        break;
                    }
                    result = restoredAttachment;
                }

                if (result != null) {
                    if (!Objects.equals(attachment.getVersion(), result.getVersion())) {
                        result = result.getAttachmentRevision(attachment.getVersion(), context);
                    }
                }
            }
        } catch (XWikiException e) {
            LOGGER.error("Error while trying to load deleted attachment [{}] for doc [{}]", attachment, doc, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {

                }
            }
        }

        if (result == null) {
            result = attachment;
        } else {
            result.setDoc(doc);
        }
        return result;
    }

    /**
     * Clone a document and change its reference.
     *
     * @param newDocumentReference the new reference of the cloned document.
     * @param context the current context.
     * @return a clone of the current document with a new reference.
     * @throws XWikiException in case of problem during the clone operation.
     * @since 12.5RC1
     */
    public XWikiDocument cloneRename(DocumentReference newDocumentReference, XWikiContext context) throws XWikiException
    {
        loadAttachments(context);
        loadArchive(context);
        return this.cloneInternal(newDocumentReference, true, true);
    }

    /**
     * @since 2.2M1
     */
    public XWikiDocument copyDocument(DocumentReference newDocumentReference, XWikiContext context)
        throws XWikiException
    {
        return copyDocument(newDocumentReference, true, context);
    }

    /**
     * @since 14.3RC1
     */
    public XWikiDocument copyDocument(DocumentReference newDocumentReference, boolean cloneArchive,
        XWikiContext context) throws XWikiException
    {
        loadAttachments(context);
        if (cloneArchive) {
            loadArchive(context);
        }

        XWikiDocument newdoc = cloneInternal(newDocumentReference, false, cloneArchive);

        // If the copied document has a title set to the original page name then set the new title to be the new page
        // name.
        if (Strings.CS.equals(newdoc.getTitle(), getPrettyName(this.getDocumentReference()))) {
            newdoc.setTitle(getPrettyName(newDocumentReference));
        }

        newdoc.setOriginalDocument(null);
        newdoc.setContentDirty(true);
        newdoc.setNew(true);

        return newdoc;
    }

    /**
     * Avoid the technical "WebHome" name.
     *
     * @param documentReference a document reference
     * @return the last space name if the document is the home of a space, the document name otherwise
     */
    private String getPrettyName(DocumentReference documentReference)
    {
        EntityReferenceProvider defaultEntityReferenceProvider = Utils.getComponent(EntityReferenceProvider.class);
        if (defaultEntityReferenceProvider.getDefaultReference(documentReference.getType()).getName()
            .equals(documentReference.getName())) {
            return documentReference.getLastSpaceReference().getName();
        }
        return documentReference.getName();
    }

    /**
     * @deprecated use {@link #copyDocument(DocumentReference, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M1")
    public XWikiDocument copyDocument(String newDocumentName, XWikiContext context) throws XWikiException
    {
        return copyDocument(getCurrentMixedDocumentReferenceResolver().resolve(newDocumentName), context);
    }

    public XWikiLock getLock(XWikiContext context) throws XWikiException
    {
        XWikiLock theLock = getStore(context).loadLock(getId(), context, true);
        if (theLock != null) {
            int timeout = context.getWiki().getXWikiPreferenceAsInt("lock_Timeout", 30 * 60, context);
            if (theLock.getDate().getTime() + timeout * 1000 < new Date().getTime()) {
                getStore(context).deleteLock(theLock, context, true);
                theLock = null;
            }
        }

        return theLock;
    }

    public void setLock(String userName, XWikiContext context) throws XWikiException
    {
        XWikiLock lock = new XWikiLock(getId(), userName);
        getStore(context).saveLock(lock, context, true);
    }

    public void removeLock(XWikiContext context) throws XWikiException
    {
        XWikiLock lock = getStore(context).loadLock(getId(), context, true);
        if (lock != null) {
            getStore(context).deleteLock(lock, context, true);
        }
    }

    public void insertText(String text, String marker, XWikiContext context) throws XWikiException
    {
        setContent(StringUtils.replaceOnce(getContent(), marker, text + marker));
        context.getWiki().saveDocument(this, context);
    }

    public Object getWikiNode()
    {
        return this.wikiNode;
    }

    public void setWikiNode(Object wikiNode)
    {
        this.wikiNode = wikiNode;
    }

    /**
     * @since 2.2M1
     */
    public String getXClassXML()
    {
        return this.xClassXML;
    }

    /**
     * @deprecated use {@link #getXClassXML()} instead as Hibernate uses this through reflection. It cannot be removed
     *             without altering hibernate.cfg.xml
     */
    @Deprecated(since = "2.2M1")
    public String getxWikiClassXML()
    {
        return getXClassXML();
    }

    /**
     * @since 2.2M1
     */
    public void setXClassXML(String xClassXML)
    {
        this.xClassXML = xClassXML;
    }

    /**
     * @deprecated use {@link #setXClassXML(String)} ()} instead as Hibernate uses this through reflection. It cannot be
     *             removed without altering hibernate.cfg.xml
     */
    @Deprecated(since = "2.2M1")
    public void setxWikiClassXML(String xClassXML)
    {
        setXClassXML(xClassXML);
    }

    public int getElements()
    {
        return this.elements;
    }

    public void setElements(int elements)
    {
        this.elements = elements;
    }

    public void setElement(int element, boolean toggle)
    {
        if (toggle) {
            this.elements = this.elements | element;
        } else {
            this.elements = this.elements & (~element);
        }
    }

    public boolean hasElement(int element)
    {
        return ((this.elements & element) == element);
    }

    /**
     * Gets the default edit mode for this document. An edit mode (other than the default "edit") can be enforced by
     * creating an {@code XWiki.EditModeClass} object in the current document, with the appropriate value for the
     * defaultEditMode property, or by adding this object in a sheet included by the document. This function also falls
     * back on the old {@code SheetClass}, deprecated since 3.1M2, which can be attached to included documents to
     * specify that the current document should be edited inline.
     *
     * @return the default edit mode for this document ("edit" or "inline" usually)
     * @param context the context of the request for this document
     * @throws XWikiException since XWiki 6.3M1 it's not used anymore and "edit" is returned in case of error, with an
     *             error log
     */
    public String getDefaultEditMode(XWikiContext context) throws XWikiException
    {
        try {
            return getDefaultEditModeInternal(context);
        } catch (Exception e) {
            // If an error happens then we default to the "edit" mode. We don't want to fail by throwing an exception
            // since it'll lead to several errors in the UI (such as when evaluating contentview.vm for example).
            LOGGER.error("Failed to get the default edit mode for [{}]", getDocumentReference(), e);
            return "edit";
        }
    }

    private String getDefaultEditModeInternal(XWikiContext context) throws XWikiException
    {
        String editModeProperty = "defaultEditMode";
        DocumentReference editModeClass =
            getCurrentReferenceDocumentReferenceResolver().resolve(XWikiConstant.EDIT_MODE_CLASS);
        // check if the current document has any edit mode class object attached to it, and read the edit mode from it
        BaseObject editModeObject = this.getXObject(editModeClass);
        if (editModeObject != null) {
            String defaultEditMode = editModeObject.getStringValue(editModeProperty);
            if (StringUtils.isEmpty(defaultEditMode)) {
                return "edit";
            } else {
                return defaultEditMode;
            }
        }
        // otherwise look for included documents
        com.xpn.xwiki.XWiki xwiki = context.getWiki();
        if (is10Syntax()) {
            if (getContent().indexOf("includeForm(") != -1) {
                return "inline";
            }
        } else {
            // Algorithm: look in all include macros and for all document included check if one of them
            // has an EditModeClass object attached to it, or a SheetClass object (deprecated since 3.1M2) attached to
            // it. If so then the edit mode is inline.

            // Find all include macros and extract the document names
            // TODO: Is there a good way not to hardcode the macro name? The macro itself shouldn't know
            // its own name since it's a deployment time concern.
            for (Block macroBlock : getXDOM().getBlocks(new MacroBlockMatcher("include"), Axes.CHILD)) {
                // Find the document reference to include by checking the macro's "reference" parameter.
                // For backward-compatibility we also check for a "document" parameter since this is the parameter name
                // that was used prior to XWiki 3.4M1 when the "reference" one was introduced and thus when the
                // "document" one was deprecated.
                String includedDocumentReference = macroBlock.getParameter("reference");
                if (includedDocumentReference == null) {
                    includedDocumentReference = macroBlock.getParameter("document");
                }
                if (includedDocumentReference != null) {
                    // Resolve the document name into a valid Reference
                    DocumentReference documentReference =
                        getCurrentMixedDocumentReferenceResolver().resolve(includedDocumentReference);
                    XWikiDocument includedDocument = xwiki.getDocument(documentReference, context);
                    if (!includedDocument.isNew()) {
                        // get the edit mode object, first the new class and then the deprecated class if new class
                        // is not found
                        editModeObject = includedDocument.getXObject(editModeClass);
                        if (editModeObject == null) {
                            editModeObject = includedDocument.getXObject(SHEETCLASS_REFERENCE);
                        }
                        if (editModeObject != null) {
                            // Use the user-defined default edit mode if set.
                            String defaultEditMode = editModeObject.getStringValue(editModeProperty);
                            if (StringUtils.isBlank(defaultEditMode)) {
                                // TODO: maybe here the real value should be returned if the object is edit mode class,
                                // and inline only if the object is sheetclass
                                return "inline";
                            } else {
                                return defaultEditMode;
                            }
                        }
                    }
                }
            }
        }

        return "edit";
    }

    public String getDefaultEditURL(XWikiContext context) throws XWikiException
    {
        String editMode = getDefaultEditMode(context);

        if ("inline".equals(editMode)) {
            return getEditURL("inline", "", context);
        } else {
            com.xpn.xwiki.XWiki xwiki = context.getWiki();
            String editor = xwiki.getEditorPreference(context);
            return getEditURL("edit", editor, context);
        }
    }

    public String getEditURL(String action, String mode, XWikiContext context) throws XWikiException
    {
        com.xpn.xwiki.XWiki xwiki = context.getWiki();
        String language = "";
        XWikiDocument tdoc = (XWikiDocument) context.get(CKEY_TDOC);
        String realLang = tdoc.getRealLanguage(context);
        if ((xwiki.isMultiLingual(context) == true) && (!realLang.equals(""))) {
            language = realLang;
        }

        return getEditURL(action, mode, language, context);
    }

    public String getEditURL(String action, String mode, String language, XWikiContext context)
    {
        StringBuilder editparams = new StringBuilder();
        if (!mode.equals("")) {
            editparams.append("xpage=");
            editparams.append(mode);
        }

        if (!language.equals("")) {
            if (!mode.equals("")) {
                editparams.append("&");
            }
            editparams.append("language=");
            editparams.append(language);
        }

        return getURL(action, editparams.toString(), context);
    }

    public String getDefaultTemplate()
    {
        if (this.defaultTemplate == null) {
            return "";
        } else {
            return this.defaultTemplate;
        }
    }

    public void setDefaultTemplate(String defaultTemplate)
    {
        if (!Objects.equals(this.defaultTemplate, defaultTemplate)) {
            this.defaultTemplate = defaultTemplate;

            setMetaDataDirty(true);
        }
    }

    public Vector<BaseObject> getComments()
    {
        return getComments(true);
    }

    /**
     * @return the syntax of the document
     * @since 2.3M1
     */
    @Override
    public Syntax getSyntax()
    {
        // Can't be initialized in the XWikiDocument constructor because #getDefaultDocumentSyntax() need to create a
        // XWikiDocument object to get preferences from wiki preferences pages and would thus generate an infinite loop
        if (isNew() && this.content.syntax == null) {
            this.content.syntax = getDefaultDocumentSyntax();
        }

        return this.content.syntax;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     * </p>
     *
     * @see org.xwiki.bridge.DocumentModelBridge#getSyntaxId()
     * @deprecated use {link #getSyntax()} instead
     */
    @Override
    @Deprecated(since = "2.3M1")
    public String getSyntaxId()
    {
        return getSyntax().toIdString();
    }

    /**
     * @param syntax the new syntax to set for this document
     * @see #getSyntax()
     * @since 2.3M1
     */
    public void setSyntax(Syntax syntax)
    {
        setContent(this.content.content, syntax);
    }

    /**
     * Note that this method cannot be removed for now since it's used by Hibernate for saving a XWikiDocument.
     *
     * @param syntaxId the new syntax id to set (e.g. {@code xwiki/2.0}, {@code xwiki/2.1}, etc)
     * @see #getSyntaxId()
     * @deprecated use {link #setSyntax(Syntax)} instead
     */
    @Deprecated(since = "2.3M1")
    public void setSyntaxId(String syntaxId)
    {
        Syntax syntax;

        // In order to preserve backward-compatibility with previous versions of XWiki in which the notion of Syntax Id
        // did not exist, we check the passed syntaxId parameter. Since this parameter comes from the database (it's
        // called automatically by Hibernate) it can be NULL or empty. In this case we consider the document is in
        // syntax/1.0 syntax.
        if (StringUtils.isBlank(syntaxId)) {
            syntax = Syntax.XWIKI_1_0;
        } else {
            syntax = resolveSyntax(syntaxId);
        }

        setSyntax(syntax);
    }

    /**
     * @return {@code true} if required rights defined in a {@code XWiki.RequiredRightClass} object shall be
     * enforced, meaning that editing will be limited to users with these rights and content of this document can't
     * use more rights than defined in the object, {@code false} otherwise
     * @since 16.10.0RC1
     */
    @Unstable
    public boolean isEnforceRequiredRights()
    {
        return this.enforceRequiredRights;
    }

    /**
     * @param enforceRequiredRights if required rights defined in a {@code XWiki.RequiredRightClass} object shall be
     * enforced, meaning that editing will be limited to users with these rights and content of this document can't use
     * more rights than defined in the object
     * @since 16.10.0RC1
     */
    @Unstable
    public void setEnforceRequiredRights(boolean enforceRequiredRights)
    {
        this.enforceRequiredRights = enforceRequiredRights;

        setContentDirty(true);
    }

    public Vector<BaseObject> getComments(boolean asc)
    {
        List<BaseObject> list = getXObjects(COMMENTSCLASS_REFERENCE);
        if (list == null) {
            return null;
        } else if (asc) {
            return new Vector<BaseObject>(list);
        } else {
            Vector<BaseObject> newlist = new Vector<BaseObject>();
            for (int i = list.size() - 1; i >= 0; i--) {
                newlist.add(list.get(i));
            }
            return newlist;
        }
    }

    public boolean isCurrentUserCreator(XWikiContext context)
    {
        return isCreator(context.getUserReference());
    }

    /**
     * @deprecated use {@link #isCreator(DocumentReference)} instead
     */
    @Deprecated
    public boolean isCreator(String username)
    {
        if (username.equals(XWikiRightService.GUEST_USER_FULLNAME)) {
            return false;
        }

        return username.equals(getCreator());
    }

    public boolean isCreator(DocumentReference username)
    {
        if (username == null) {
            return false;
        }

        return username.equals(getCreatorReference());
    }

    public boolean isCurrentUserPage(XWikiContext context)
    {
        DocumentReference userReference = context.getUserReference();
        if (userReference == null) {
            return false;
        }

        return userReference.equals(getDocumentReference());
    }

    public boolean isCurrentLocalUserPage(XWikiContext context)
    {
        final DocumentReference userRef = context.getUserReference();
        return userRef != null && userRef.equals(this.getDocumentReference());
    }

    public void resetArchive(XWikiContext context) throws XWikiException
    {
        boolean hasVersioning = context.getWiki().hasVersioning(context);
        if (hasVersioning) {
            WikiReference currentWiki = context.getWikiReference();
            try {
                context.setWikiReference(getDocumentReference().getWikiReference());

                getVersioningStore(context).resetRCSArchive(this, true, context);
            } finally {
                context.setWikiReference(currentWiki);
            }
        }
    }

    /**
     * Adds an object from an new object creation form.
     *
     * @since 2.2M2
     */
    public BaseObject addXObjectFromRequest(XWikiContext context) throws XWikiException
    {
        // Read info in object
        ObjectAddForm form = new ObjectAddForm();
        form.setRequest(context.getRequest());
        form.readRequest();

        EntityReference classReference = getXClassEntityReferenceResolver().resolve(form.getClassName(),
            EntityType.DOCUMENT, getDocumentReference());
        BaseObject object = newXObject(classReference, context);
        BaseClass baseclass = object.getXClass(context);
        baseclass.fromMap(form.getObject(LOCAL_REFERENCE_SERIALIZER.serialize(resolveClassReference(classReference))),
            object);

        return object;
    }

    /**
     * Adds an object from an new object creation form.
     *
     * @since 2.2.3
     */
    public BaseObject addXObjectFromRequest(EntityReference classReference, XWikiContext context) throws XWikiException
    {
        return addXObjectFromRequest(classReference, "", 0, context);
    }

    /**
     * @deprecated use {@link #addXObjectFromRequest(EntityReference, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject addObjectFromRequest(String className, XWikiContext context) throws XWikiException
    {
        return addObjectFromRequest(className, "", 0, context);
    }

    /**
     * Adds an object from an new object creation form.
     *
     * @since 2.2M2
     */
    public BaseObject addXObjectFromRequest(DocumentReference classReference, String prefix, XWikiContext context)
        throws XWikiException
    {
        return addXObjectFromRequest(classReference, prefix, 0, context);
    }

    /**
     * @deprecated use {@link #addXObjectFromRequest(DocumentReference, String, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject addObjectFromRequest(String className, String prefix, XWikiContext context) throws XWikiException
    {
        return addObjectFromRequest(className, prefix, 0, context);
    }

    /**
     * Adds multiple objects from an new objects creation form.
     *
     * @since 2.2M2
     */
    public List<BaseObject> addXObjectsFromRequest(DocumentReference classReference, XWikiContext context)
        throws XWikiException
    {
        return addXObjectsFromRequest(classReference, "", context);
    }

    /**
     * @deprecated use {@link #addXObjectsFromRequest(DocumentReference, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public List<BaseObject> addObjectsFromRequest(String className, XWikiContext context) throws XWikiException
    {
        return addObjectsFromRequest(className, "", context);
    }

    /**
     * Adds multiple objects from an new objects creation form.
     *
     * @since 2.2M2
     */
    public List<BaseObject> addXObjectsFromRequest(DocumentReference classReference, String pref, XWikiContext context)
        throws XWikiException
    {
        @SuppressWarnings("unchecked")
        Map<String, String[]> map = context.getRequest().getParameterMap();
        List<Integer> objectsNumberDone = new ArrayList<Integer>();
        List<BaseObject> objects = new ArrayList<BaseObject>();
        String start = pref + LOCAL_REFERENCE_SERIALIZER.serialize(classReference) + "_";

        for (String name : map.keySet()) {
            if (name.startsWith(start)) {
                int pos = name.indexOf('_', start.length() + 1);
                String prefix = name.substring(0, pos);
                int num = Integer.decode(prefix.substring(prefix.lastIndexOf('_') + 1)).intValue();
                if (!objectsNumberDone.contains(Integer.valueOf(num))) {
                    objectsNumberDone.add(Integer.valueOf(num));
                    objects.add(addXObjectFromRequest(classReference, pref, num, context));
                }
            }
        }

        return objects;
    }

    /**
     * @deprecated use {@link #addXObjectsFromRequest(DocumentReference, String, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public List<BaseObject> addObjectsFromRequest(String className, String pref, XWikiContext context)
        throws XWikiException
    {
        return addXObjectsFromRequest(resolveClassReference(className), pref, context);
    }

    /**
     * Adds object from an new object creation form.
     *
     * @since 2.2M2
     */
    public BaseObject addXObjectFromRequest(DocumentReference classReference, int num, XWikiContext context)
        throws XWikiException
    {
        return addXObjectFromRequest(classReference, "", num, context);
    }

    /**
     * @deprecated use {@link #addXObjectFromRequest(DocumentReference, int, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject addObjectFromRequest(String className, int num, XWikiContext context) throws XWikiException
    {
        return addObjectFromRequest(className, "", num, context);
    }

    /**
     * Adds object from an new object creation form.
     *
     * @since 2.2.3
     */
    public BaseObject addXObjectFromRequest(EntityReference classReference, String prefix, int num,
        XWikiContext context) throws XWikiException
    {
        BaseObject object = newXObject(classReference, context);
        BaseClass baseclass = object.getXClass(context);
        String newPrefix =
            prefix + LOCAL_REFERENCE_SERIALIZER.serialize(resolveClassReference(classReference)) + "_" + num;
        baseclass.fromMap(Util.getObject(context.getRequest(), newPrefix), object);

        return object;
    }

    /**
     * @deprecated use {@link #addXObjectFromRequest(EntityReference, String, int, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject addObjectFromRequest(String className, String prefix, int num, XWikiContext context)
        throws XWikiException
    {
        return addXObjectFromRequest(resolveClassReference(className), prefix, num, context);
    }

    /**
     * Adds an object from an new object creation form.
     *
     * @since 2.2.3
     */
    public BaseObject updateXObjectFromRequest(EntityReference classReference, XWikiContext context)
        throws XWikiException
    {
        return updateXObjectFromRequest(classReference, "", context);
    }

    /**
     * @deprecated use {@link #updateXObjectFromRequest(EntityReference, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject updateObjectFromRequest(String className, XWikiContext context) throws XWikiException
    {
        return updateObjectFromRequest(className, "", context);
    }

    /**
     * Adds an object from an new object creation form.
     *
     * @since 2.2.3
     */
    public BaseObject updateXObjectFromRequest(EntityReference classReference, String prefix, XWikiContext context)
        throws XWikiException
    {
        return updateXObjectFromRequest(classReference, prefix, 0, context);
    }

    /**
     * @deprecated use {@link #updateXObjectFromRequest(EntityReference, String, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject updateObjectFromRequest(String className, String prefix, XWikiContext context)
        throws XWikiException
    {
        return updateObjectFromRequest(className, prefix, 0, context);
    }

    /**
     * Adds an object from an new object creation form.
     *
     * @since 2.2.3
     */
    public BaseObject updateXObjectFromRequest(EntityReference classReference, String prefix, int num,
        XWikiContext context) throws XWikiException
    {
        DocumentReference absoluteClassReference = resolveClassReference(classReference);
        int nb;
        BaseObject oldobject = getXObject(absoluteClassReference, num);
        if (oldobject == null) {
            nb = createXObject(classReference, context);
            oldobject = getXObject(absoluteClassReference, nb);
        } else {
            nb = oldobject.getNumber();
        }
        BaseClass baseclass = oldobject.getXClass(context);
        String newPrefix = prefix + LOCAL_REFERENCE_SERIALIZER.serialize(absoluteClassReference) + "_" + nb;
        BaseObject newobject =
            (BaseObject) baseclass.fromMap(Util.getObject(context.getRequest(), newPrefix), oldobject);
        newobject.setNumber(oldobject.getNumber());
        newobject.setGuid(oldobject.getGuid());
        setXObject(nb, newobject);

        return newobject;
    }

    /**
     * @deprecated use {@link #updateXObjectFromRequest(EntityReference, String, int, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject updateObjectFromRequest(String className, String prefix, int num, XWikiContext context)
        throws XWikiException
    {
        return updateXObjectFromRequest(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()), prefix,
            num, context);
    }

    /**
     * Adds an object from an new object creation form.
     *
     * @since 2.2.3
     */
    public List<BaseObject> updateXObjectsFromRequest(EntityReference classReference, XWikiContext context)
        throws XWikiException
    {
        return updateXObjectsFromRequest(classReference, "", context);
    }

    /**
     * @deprecated use {@link #updateXObjectsFromRequest(EntityReference, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public List<BaseObject> updateObjectsFromRequest(String className, XWikiContext context) throws XWikiException
    {
        return updateObjectsFromRequest(className, "", context);
    }

    /**
     * Adds multiple objects from an new objects creation form.
     *
     * @since 2.2.3
     */
    public List<BaseObject> updateXObjectsFromRequest(EntityReference classReference, String pref, XWikiContext context)
        throws XWikiException
    {
        DocumentReference absoluteClassReference = resolveClassReference(classReference);
        @SuppressWarnings("unchecked")
        Map<String, String[]> map = context.getRequest().getParameterMap();
        List<Integer> objectsNumberDone = new ArrayList<Integer>();
        List<BaseObject> objects = new ArrayList<BaseObject>();
        String start = pref + LOCAL_REFERENCE_SERIALIZER.serialize(absoluteClassReference) + "_";

        for (String name : map.keySet()) {
            if (name.startsWith(start)) {
                int pos = name.indexOf('_', start.length() + 1);
                String prefix = name.substring(0, pos);
                int num = Integer.decode(prefix.substring(prefix.lastIndexOf('_') + 1)).intValue();
                if (!objectsNumberDone.contains(Integer.valueOf(num))) {
                    objectsNumberDone.add(Integer.valueOf(num));
                    objects.add(updateXObjectFromRequest(classReference, pref, num, context));
                }
            }
        }

        return objects;
    }

    /**
     * @deprecated use {@link #updateXObjectsFromRequest(EntityReference, String, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public List<BaseObject> updateObjectsFromRequest(String className, String pref, XWikiContext context)
        throws XWikiException
    {
        return updateXObjectsFromRequest(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()), pref,
            context);
    }

    public boolean isAdvancedContent()
    {
        String[] matches = {"<%", "#set", "#include", "#if", "public class", "/* Advanced content */",
            "## Advanced content", "/* Programmatic content */", "## Programmatic content"};
        String content2 = getContent().toLowerCase();
        for (String match : matches) {
            if (content2.indexOf(match.toLowerCase()) != -1) {
                return true;
            }
        }

        if (HTML_TAG_PATTERN.matcher(content2).find()) {
            return true;
        }

        return false;
    }

    public boolean isProgrammaticContent()
    {
        String[] matches = {"<%", "\\$xwiki.xWiki", "$xcontext.context", "$doc.document", "$xwiki.getXWiki()",
            "$xcontext.getContext()", "$doc.getDocument()", "WithProgrammingRights(", "/* Programmatic content */",
            "## Programmatic content", "$xwiki.search(", "$xwiki.createUser", "$xwiki.createNewWiki",
            "$xwiki.addToAllGroup", "$xwiki.sendMessage", "$xwiki.copyDocument", "$xwiki.copyWikiWeb",
            "$xwiki.copySpaceBetweenWikis", "$xwiki.parseGroovyFromString", "$doc.toXML()", "$doc.toXMLDocument()",};
        String content2 = getContent().toLowerCase();
        for (String match : matches) {
            if (content2.indexOf(match.toLowerCase()) != -1) {
                return true;
            }
        }

        return false;
    }

    /**
     * Remove an XObject from the document. The changes are not persisted until the document is saved.
     *
     * @param object the object to remove
     * @return {@code true} if the object was successfully removed, {@code false} if the object was not found in the
     *         current document.
     * @since 2.2M1
     */
    public boolean removeXObject(BaseObject object)
    {
        List<BaseObject> objects = this.xObjects.get(object.getXClassReference());
        // No objects at all, nothing to remove
        if (objects == null) {
            return false;
        }
        // Sometimes the object vector is wrongly indexed, meaning that objects are not at the right position
        // Check if the right object is in place
        int objectPosition = object.getNumber();
        if (objectPosition < objects.size()) {
            BaseObject storedObject = objects.get(objectPosition);
            if (storedObject == null || !storedObject.equals(object)) {
                // Try to find the correct position
                objectPosition = objects.indexOf(object);
            }
        } else {
            // The object position is greater than the array, that's invalid!
            objectPosition = -1;
        }
        // If the object is not in the document, simply ignore this request
        if (objectPosition < 0) {
            return false;
        }
        // We don't remove objects, but set null in their place, so that the object number corresponds to its position
        // in the vector
        objects.set(objectPosition, null);
        // Schedule the object for removal from the storage
        addXObjectToRemove(object);

        return true;
    }

    /**
     * Remove an XObject from the document. The changes are not persisted until the document is saved.
     *
     * @param object the object to remove
     * @return {@code true} if the object was successfully removed, {@code false} if the object was not found in the
     *         current document.
     * @deprecated use {@link #removeXObject(com.xpn.xwiki.objects.BaseObject)} instead
     */
    @Deprecated(since = "2.2M1")
    public boolean removeObject(BaseObject object)
    {
        return removeXObject(object);
    }

    /**
     * Remove all the objects of a given type (XClass) from the document. The object counter is left unchanged, so that
     * future objects will have new (different) numbers. However, on some storage engines the counter will be reset if
     * the document is removed from the cache and reloaded from the persistent storage.
     *
     * @param classReference The XClass reference of the XObjects to be removed.
     * @return {@code true} if the objects were successfully removed, {@code false} if no object from the target class
     *         was in the current document.
     * @since 2.2M1
     */
    public boolean removeXObjects(DocumentReference classReference)
    {
        List<BaseObject> objects = this.xObjects.get(classReference);
        // No objects at all, nothing to remove
        if (objects == null) {
            return false;
        }
        // Schedule the object for removal from the storage
        for (BaseObject object : objects) {
            if (object != null) {
                addXObjectToRemove(object);
            }
        }
        // Empty the vector, retaining its size
        int currentSize = objects.size();
        objects.clear();
        for (int i = 0; i < currentSize; i++) {
            objects.add(null);
        }

        return true;
    }

    /**
     * Remove all the objects of a given type (XClass) from the document. The object counter is left unchanged, so that
     * future objects will have new (different) numbers. However, on some storage engines the counter will be reset if
     * the document is removed from the cache and reloaded from the persistent storage.
     *
     * @param reference The XClass reference of the XObjects to be removed.
     * @return {@code true} if the objects were successfully removed, {@code false} if no object from the target class
     *         was in the current document.
     * @since 5.0M1
     */
    public boolean removeXObjects(EntityReference reference)
    {
        return removeXObjects(
            getCurrentReferenceDocumentReferenceResolver().resolve(reference, getDocumentReference()));
    }

    /**
     * Remove all the objects of a given type (XClass) from the document. The object counter is left unchanged, so that
     * future objects will have new (different) numbers. However, on some storage engines the counter will be reset if
     * the document is removed from the cache and reloaded from the persistent storage.
     *
     * @param className The class name of the objects to be removed.
     * @return {@code true} if the objects were successfully removed, {@code false} if no object from the target class
     *         was in the current document.
     * @deprecated use {@link #removeXObjects(org.xwiki.model.reference.DocumentReference)} instead
     */
    @Deprecated(since = "2.2M1")
    public boolean removeObjects(String className)
    {
        return removeXObjects(resolveClassReference(className));
    }

    /**
     * Get the top sections contained in the document.
     * <p>
     * The section are filtered by xwiki.section.depth property on the maximum depth of the sections to return. This
     * method is usually used to get "editable" sections.
     *
     * @return the sections in the current document
     */
    public List<DocumentSection> getSections() throws XWikiException
    {
        if (is10Syntax()) {
            return getSections10();
        } else {
            List<DocumentSection> splitSections = new ArrayList<DocumentSection>();
            List<HeaderBlock> headers = getFilteredHeaders();

            int sectionNumber = 1;
            for (HeaderBlock header : headers) {
                // put -1 as index since there is no way to get the position of the header in the source
                int documentSectionIndex = -1;

                // Need to do the same thing than 1.0 content here
                String documentSectionLevel = StringUtils.repeat("1.", header.getLevel().getAsInt() - 1) + "1";

                DocumentSection docSection = new DocumentSection(sectionNumber++, documentSectionIndex,
                    documentSectionLevel, renderXDOM(new XDOM(header.getChildren()), getSyntax()));
                splitSections.add(docSection);
            }

            return splitSections;
        }
    }

    /**
     * Get XWiki context from execution context.
     *
     * @return the XWiki context for the current thread
     */
    private XWikiContext getXWikiContext()
    {
        Provider<XWikiContext> xcontextProvider = Utils.getComponent(XWikiContext.TYPE_PROVIDER);

        if (xcontextProvider != null) {
            return xcontextProvider.get();
        }

        return null;
    }

    /**
     * Filter the headers from a document XDOM based on xwiki.section.depth property from xwiki.cfg file.
     *
     * @return the filtered headers
     */
    private List<HeaderBlock> getFilteredHeaders()
    {
        List<HeaderBlock> filteredHeaders = new ArrayList<HeaderBlock>();

        // Get the maximum header level
        int sectionDepth = 2;
        XWikiContext context = getXWikiContext();
        if (context != null) {
            sectionDepth = (int) context.getWiki().getSectionEditingDepth();
        }

        // Get the headers.
        //
        // Note that we need to only take into account SectionBlock that are children of other SectionBlocks so that
        // we are in sync with the section editing buttons added in xwiki.js. Being able to section edit any heading is
        // too complex. For example if you have (in XWiki Syntax 2.0):
        // = Heading1 =
        // para1
        // == Heading2 ==
        // para2
        // (((
        // == Heading3 ==
        // para3
        // (((
        // == Heading4 ==
        // para4
        // )))
        // )))
        // == Heading5 ==
        // para5
        //
        // Then if we were to support editing "Heading4", its content would be:
        // para4
        // )))
        // )))
        //
        // Which obviously is not correct...

        final XDOM xdom = getXDOM();
        if (!xdom.getChildren().isEmpty()) {
            Block currentBlock = xdom.getChildren().get(0);
            while (currentBlock != null) {
                if (currentBlock instanceof SectionBlock) {
                    // The next children block is a HeaderBlock but we check to be on the safe side...
                    Block nextChildrenBlock = currentBlock.getChildren().get(0);
                    if (nextChildrenBlock instanceof HeaderBlock) {
                        HeaderBlock headerBlock = (HeaderBlock) nextChildrenBlock;
                        if (headerBlock.getLevel().getAsInt() <= sectionDepth) {
                            filteredHeaders.add(headerBlock);
                        }
                    }
                    currentBlock = nextChildrenBlock;
                } else {
                    Block nextSibling = currentBlock.getNextSibling();
                    if (nextSibling == null) {
                        currentBlock = currentBlock.getParent();
                        while (currentBlock != null) {
                            if (currentBlock.getNextSibling() != null) {
                                currentBlock = currentBlock.getNextSibling();
                                break;
                            }
                            currentBlock = currentBlock.getParent();
                        }
                    } else {
                        currentBlock = nextSibling;
                    }
                }
            }
        }

        return filteredHeaders;
    }

    /**
     * @return the sections in the current document
     */
    private List<DocumentSection> getSections10()
    {
        // Pattern to match the title. Matches only level 1 and level 2 headings.
        Pattern headingPattern = Pattern.compile("^[ \\t]*+(1(\\.1){0,1}+)[ \\t]++(.++)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(getContent());
        List<DocumentSection> splitSections = new ArrayList<DocumentSection>();
        int sectionNumber = 0;
        // find title to split
        while (matcher.find()) {
            ++sectionNumber;
            String sectionLevel = matcher.group(1);
            String sectionTitle = matcher.group(3);
            int sectionIndex = matcher.start();
            // Initialize a documentSection object.
            DocumentSection docSection = new DocumentSection(sectionNumber, sectionIndex, sectionLevel, sectionTitle);
            // Add the document section to list.
            splitSections.add(docSection);
        }

        return splitSections;
    }

    /**
     * Return a Document section with parameter is sectionNumber.
     *
     * @param sectionNumber the index (+1) of the section in the list of all sections in the document.
     * @return
     * @throws XWikiException error when extracting sections from document
     */
    public DocumentSection getDocumentSection(int sectionNumber) throws XWikiException
    {
        // return a document section according to section number
        return getSections().get(sectionNumber - 1);
    }

    /**
     * Return the content of a section.
     *
     * @param sectionNumber the index (+1) of the section in the list of all sections in the document.
     * @return the content of a section or null if the section can't be found.
     * @throws XWikiException error when trying to extract section content
     */
    public String getContentOfSection(int sectionNumber) throws XWikiException
    {
        String content = null;

        if (is10Syntax()) {
            content = getContentOfSection10(sectionNumber);
        } else {
            List<HeaderBlock> headers = getFilteredHeaders();

            if (headers.size() >= sectionNumber) {
                SectionBlock section = headers.get(sectionNumber - 1).getSection();
                content = renderXDOM(new XDOM(Collections.<Block>singletonList(section)), getSyntax());
            }
        }

        return content;
    }

    /**
     * Return the content of a section.
     *
     * @param sectionNumber the index (+1) of the section in the list of all sections in the document.
     * @return the content of a section
     * @throws XWikiException error when trying to extract section content
     */
    private String getContentOfSection10(int sectionNumber) throws XWikiException
    {
        List<DocumentSection> splitSections = getSections();
        int indexEnd = 0;
        // get current section
        DocumentSection section = splitSections.get(sectionNumber - 1);
        int indexStart = section.getSectionIndex();
        String sectionLevel = section.getSectionLevel();
        // Determine where this section ends, which is at the start of the next section of the
        // same or a higher level.
        for (int i = sectionNumber; i < splitSections.size(); i++) {
            DocumentSection nextSection = splitSections.get(i);
            String nextLevel = nextSection.getSectionLevel();
            if (sectionLevel.equals(nextLevel) || sectionLevel.length() > nextLevel.length()) {
                indexEnd = nextSection.getSectionIndex();
                break;
            }
        }
        String sectionContent = null;
        if (indexStart < 0) {
            indexStart = 0;
        }

        if (indexEnd == 0) {
            sectionContent = getContent().substring(indexStart);
        } else {
            sectionContent = getContent().substring(indexStart, indexEnd);
        }

        return sectionContent;
    }

    /**
     * Update a section content in document.
     *
     * @param sectionNumber the index (starting at 1) of the section in the list of all sections in the document.
     * @param newSectionContent the new section content.
     * @return the new document content.
     * @throws XWikiException error when updating content
     */
    public String updateDocumentSection(int sectionNumber, String newSectionContent) throws XWikiException
    {
        String content;
        if (is10Syntax()) {
            content = updateDocumentSection10(sectionNumber, newSectionContent);
        } else {
            // Get the current section block
            HeaderBlock header = getFilteredHeaders().get(sectionNumber - 1);

            XDOM xdom = (XDOM) header.getRoot();

            // newSectionContent -> Blocks
            List<Block> blocks = parseContent(newSectionContent).getChildren();
            int sectionLevel = header.getLevel().getAsInt();
            for (int level = 1; level < sectionLevel && blocks.size() == 1
                && blocks.get(0) instanceof SectionBlock; ++level) {
                blocks = blocks.get(0).getChildren();
            }

            // replace old current SectionBlock with new Blocks
            Block section = header.getSection();
            section.getParent().replaceChild(blocks, section);

            // render back XDOM to document's content syntax
            content = renderXDOM(xdom, getSyntax());
        }

        return content;
    }

    /**
     * Update a section content in document.
     *
     * @param sectionNumber the index (+1) of the section in the list of all sections in the document.
     * @param newSectionContent the new section content.
     * @return the new document content.
     * @throws XWikiException error when updating document content with section content
     */
    private String updateDocumentSection10(int sectionNumber, String newSectionContent) throws XWikiException
    {
        StringBuilder newContent = new StringBuilder();
        // get document section that will be edited
        DocumentSection docSection = getDocumentSection(sectionNumber);
        int numberOfSections = getSections().size();
        int indexSection = docSection.getSectionIndex();
        if (numberOfSections == 1) {
            // there is only a sections in document
            String contentBegin = getContent().substring(0, indexSection);
            newContent = newContent.append(contentBegin).append(newSectionContent);
            return newContent.toString();
        } else if (sectionNumber == numberOfSections) {
            // edit lastest section that doesn't contain subtitle
            String contentBegin = getContent().substring(0, indexSection);
            newContent = newContent.append(contentBegin).append(newSectionContent);
            return newContent.toString();
        } else {
            String sectionLevel = docSection.getSectionLevel();
            int nextSectionIndex = 0;
            // get index of next section
            for (int i = sectionNumber; i < numberOfSections; i++) {
                DocumentSection nextSection = getDocumentSection(i + 1); // get next section
                String nextSectionLevel = nextSection.getSectionLevel();
                if (sectionLevel.equals(nextSectionLevel)) {
                    nextSectionIndex = nextSection.getSectionIndex();
                    break;
                } else if (sectionLevel.length() > nextSectionLevel.length()) {
                    nextSectionIndex = nextSection.getSectionIndex();
                    break;
                }
            }

            if (nextSectionIndex == 0) {// edit the last section
                newContent = newContent.append(getContent().substring(0, indexSection)).append(newSectionContent);
                return newContent.toString();
            } else {
                String contentAfter = getContent().substring(nextSectionIndex);
                String contentBegin = getContent().substring(0, indexSection);
                newContent = newContent.append(contentBegin).append(newSectionContent).append(contentAfter);
            }

            return newContent.toString();
        }
    }

    /**
     * Computes a document hash, taking into account all document data: content, objects, attachments, metadata... TODO:
     * cache the hash value, update only on modification.
     */
    public String getVersionHashCode(XWikiContext context)
    {
        MessageDigest md5 = null;

        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            LOGGER.error("Cannot create MD5 object", ex);
            return hashCode() + "";
        }

        try {
            String valueBeforeMD5 = toXML(true, false, true, false, context);
            md5.update(valueBeforeMD5.getBytes());

            byte[] array = md5.digest();
            StringBuilder sb = new StringBuilder();
            for (byte element : array) {
                int b = element & 0xFF;
                if (b < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(b));
            }

            return sb.toString();
        } catch (Exception ex) {
            LOGGER.error("Exception while computing document hash", ex);
        }

        return hashCode() + "";
    }

    public static String getInternalPropertyName(String propname, XWikiContext context)
    {
        ContextualLocalizationManager localizationManager = Utils.getComponent(ContextualLocalizationManager.class);
        String cpropname = StringUtils.capitalize(propname);

        return localizationManager == null ? cpropname : localizationManager.getTranslationPlain(cpropname);
    }

    public String getInternalProperty(String propname)
    {
        String methodName = "get" + StringUtils.capitalize(propname);
        try {
            Method method = getClass().getDeclaredMethod(methodName, (Class[]) null);
            return (String) method.invoke(this, (Object[]) null);
        } catch (Exception e) {
            return null;
        }
    }

    public String getCustomClass()
    {
        if (this.customClass == null) {
            return "";
        }

        return this.customClass;
    }

    public void setCustomClass(String customClass)
    {
        if (!Objects.equals(this.customClass, customClass)) {
            this.customClass = customClass;
            setMetaDataDirty(true);
        }
    }

    public void setValidationScript(String validationScript)
    {
        if (!Objects.equals(this.validationScript, validationScript)) {
            this.validationScript = validationScript;

            setMetaDataDirty(true);
        }
    }

    public String getValidationScript()
    {
        if (this.validationScript == null) {
            return "";
        } else {
            return this.validationScript;
        }
    }

    public String getComment()
    {
        if (this.comment == null) {
            return "";
        }

        return this.comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }

    public boolean isMinorEdit()
    {
        return this.isMinorEdit;
    }

    public void setMinorEdit(boolean isMinor)
    {
        this.isMinorEdit = isMinor;
    }

    // methods for easy table update. It is need only for hibernate.
    // when hibernate update old database without minorEdit field, hibernate will create field with
    // null in despite of notnull in hbm.
    // (http://opensource.atlassian.com/projects/hibernate/browse/HB-1151)
    // so minorEdit will be null for old documents. But hibernate can't convert null to boolean.
    // so we need convert Boolean to boolean
    protected Boolean getMinorEdit1()
    {
        return Boolean.valueOf(isMinorEdit());
    }

    protected void setMinorEdit1(Boolean isMinor)
    {
        this.isMinorEdit = (isMinor != null && isMinor.booleanValue());
    }

    /**
     * Create, add and return a new object with the provided class.
     * <p>
     * Note that absolute reference are not supported for xclasses which mean that the wiki part (whatever the wiki is)
     * of the reference will be systematically removed.
     *
     * @param classReference the reference of the class
     * @param context the XWiki context
     * @return the newly created object
     * @throws XWikiException error when creating the new object
     * @since 2.2.3
     */
    public BaseObject newXObject(EntityReference classReference, XWikiContext context) throws XWikiException
    {
        int nb = createXObject(classReference, context);
        return getXObject(resolveClassReference(classReference), nb);
    }

    /**
     * @deprecated use {@link #newXObject(EntityReference, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject newObject(String className, XWikiContext context) throws XWikiException
    {
        return newXObject(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()),
            context);
    }

    /**
     * @since 2.2M2
     */
    public BaseObject getXObject(DocumentReference classReference, boolean create, XWikiContext context)
    {
        try {
            BaseObject obj = getXObject(classReference);

            if ((obj == null) && create) {
                return newXObject(classReference, context);
            }

            if (obj == null) {
                return null;
            } else {
                return obj;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @since 3.4M1
     */
    public BaseObject getXObject(EntityReference classReference, boolean create, XWikiContext context)
    {
        try {
            BaseObject obj = getXObject(classReference);

            if ((obj == null) && create) {
                return newXObject(classReference, context);
            }

            if (obj == null) {
                return null;
            } else {
                return obj;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @deprecated use {@link #getXObject(DocumentReference, boolean, XWikiContext)} instead
     */
    @Deprecated(since = "2.2M2")
    public BaseObject getObject(String className, boolean create, XWikiContext context)
    {
        return getXObject(
            getXClassEntityReferenceResolver().resolve(className, EntityType.DOCUMENT, getDocumentReference()), create,
            context);
    }

    public boolean validate(XWikiContext context) throws XWikiException
    {
        return validate(null, context);
    }

    public boolean validate(String[] classNames, XWikiContext context) throws XWikiException
    {
        boolean isValid = true;
        if ((classNames == null) || (classNames.length == 0)) {
            for (DocumentReference classReference : getXObjects().keySet()) {
                BaseClass bclass = context.getWiki().getXClass(classReference, context);
                List<BaseObject> objects = getXObjects(classReference);
                for (BaseObject obj : objects) {
                    if (obj != null) {
                        isValid &= bclass.validateObject(obj, context);
                    }
                }
            }
        } else {
            for (String className : classNames) {
                List<BaseObject> objects = getXObjects(getCurrentMixedDocumentReferenceResolver().resolve(className));
                if (objects != null) {
                    for (BaseObject obj : objects) {
                        if (obj != null) {
                            BaseClass bclass = obj.getXClass(context);
                            isValid &= bclass.validateObject(obj, context);
                        }
                    }
                }
            }
        }

        String validationScript = "";
        XWikiRequest req = context.getRequest();
        if (req != null) {
            validationScript = req.get("xvalidation");
        }

        if ((validationScript == null) || (validationScript.trim().equals(""))) {
            validationScript = getValidationScript();
        }

        if ((validationScript != null) && (!validationScript.trim().equals(""))) {
            isValid &= executeValidationScript(context, validationScript);
        }

        return isValid;
    }

    public static void backupContext(Map<String, Object> backup, XWikiContext context)
    {
        // The XWiki Context isn't recreated when the Execution Context is cloned so we have to backup some of its data.
        // Backup the current document on the XWiki Context.
        backup.put("doc", context.getDoc());

        backup.put(CKEY_CDOC, context.get(CKEY_CDOC));
        backup.put(CKEY_TDOC, context.get(CKEY_TDOC));

        // Backup the secure document
        backup.put(CKEY_SDOC, context.get(CKEY_SDOC));

        // Clone the Execution Context to provide isolation. The clone will have a new Velocity and Script Context.
        Execution execution = Utils.getComponent(Execution.class);
        try {
            execution.pushContext(Utils.getComponent(ExecutionContextManager.class).clone(execution.getContext()));
        } catch (ExecutionContextException e) {
            throw new RuntimeException("Failed to clone the Execution Context", e);
        }

        // Bridge with old XWiki Context, required for legacy code.
        execution.getContext().setProperty(XWikiContext.EXECUTIONCONTEXT_KEY, context);
    }

    public static void restoreContext(Map<String, Object> backup, XWikiContext context)
    {
        // Restore the Execution Context. This will also restore the previous Velocity and Script Context.
        Execution execution = Utils.getComponent(Execution.class);
        execution.popContext();

        // Restore the current document on the XWiki Context.
        context.setDoc((XWikiDocument) backup.get("doc"));

        context.put(CKEY_CDOC, backup.get(CKEY_CDOC));
        context.put(CKEY_TDOC, backup.get(CKEY_TDOC));

        // Restore the secure document
        context.put(CKEY_SDOC, backup.get(CKEY_SDOC));
    }

    public void setAsContextDoc(XWikiContext context)
    {
        context.setDoc(this);
        context.remove(CKEY_CDOC);
        context.remove(CKEY_TDOC);

        // Get rid of secure document (so that it fallback on context document)
        context.remove(CKEY_SDOC);
    }

    /**
     * @return the String representation of the previous version of this document or null if this is the first version.
     */
    public String getPreviousVersion()
    {
        XWikiDocumentArchive archive = loadDocumentArchive();
        if (archive != null) {
            Version prevVersion = archive.getPrevVersion(getRCSVersion());
            if (prevVersion != null) {
                return prevVersion.toString();
            }
        }
        return null;
    }

    @Override
    public String toString()
    {
        return getFullName();
    }

    /**
     * Indicates whether the document should be 'hidden' or not, meaning that it should not be returned in public search
     * results.
     *
     * @param hidden The new value of the {@link #hidden} property.
     */
    public void setHidden(Boolean hidden)
    {
        boolean hiddenValue;
        if (hidden == null) {
            hiddenValue = false;
        } else {
            hiddenValue = hidden;
        }

        if (this.hidden != hiddenValue) {
            this.hidden = hiddenValue;

            setMetaDataDirty(hiddenValue);
        }
    }

    /**
     * Indicates whether the document is 'hidden' or not, meaning that it should not be returned in public search
     * results.
     *
     * @return <code>true</code> if the document is hidden and does not appear among the results of
     *         {@link com.xpn.xwiki.api.XWiki#searchDocuments(String)}, <code>false</code> otherwise.
     */
    @Override
    public Boolean isHidden()
    {
        return this.hidden;
    }

    /**
     * Convert the current document content from its current syntax to the new syntax passed as parameter.
     *
     * @param targetSyntaxId the syntax to convert to (e.g. {@code xwiki/2.0}, {@code xhtml/1.0}, etc)
     * @throws XWikiException if an exception occurred during the conversion process
     */
    public void convertSyntax(String targetSyntaxId, XWikiContext context) throws XWikiException
    {
        try {
            convertSyntax(Syntax.valueOf(targetSyntaxId), context);
        } catch (Exception e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_RENDERING, XWikiException.ERROR_XWIKI_UNKNOWN,
                "Failed to convert document to syntax [" + targetSyntaxId + "]", e);
        }
    }

    /**
     * Convert the current document content from its current syntax to the new syntax passed as parameter.
     *
     * @param targetSyntax the syntax to convert to (e.g. {@code xwiki/2.0}, {@code xhtml/1.0}, etc)
     * @throws XWikiException if an exception occurred during the conversion process
     */
    public void convertSyntax(Syntax targetSyntax, XWikiContext context) throws XWikiException
    {
        // convert content
        setContent(performSyntaxConversion(getContent(), getDocumentReference(), getSyntax(), targetSyntax));

        // convert objects
        Map<DocumentReference, List<BaseObject>> objectsByClass = getXObjects();

        for (List<BaseObject> objects : objectsByClass.values()) {
            for (BaseObject bobject : objects) {
                if (bobject != null) {
                    BaseClass bclass = bobject.getXClass(context);
                    for (Object fieldClass : bclass.getProperties()) {
                        if (fieldClass instanceof TextAreaClass && ((TextAreaClass) fieldClass).isWikiContent()) {
                            TextAreaClass textAreaClass = (TextAreaClass) fieldClass;
                            PropertyInterface field = bobject.getField(textAreaClass.getName());

                            // Make sure the field is the right type (might happen while a document is being migrated)
                            if (field instanceof LargeStringProperty) {
                                LargeStringProperty largeField = (LargeStringProperty) field;

                                largeField.setValue(performSyntaxConversion(largeField.getValue(),
                                    getDocumentReference(), getSyntax(), targetSyntax));
                            }
                        }
                    }
                }
            }
        }

        // change syntax
        setSyntax(targetSyntax);
    }

    private XDOM parseContentNoException()
    {
        try {
            return parseContent(getContent());
        } catch (Exception e) {
            ErrorBlockGenerator errorBlockGenerator = Utils.getComponent(ErrorBlockGenerator.class);
            return new XDOM(errorBlockGenerator.generateErrorBlocks(false, TM_FAILEDDOCUMENTPARSE,
                "Failed to parse document content", null, e));
        }
    }

    /**
     * NOTE: This method caches the XDOM and returns a clone that can be safely modified.
     *
     * @return the XDOM corresponding to the document's string content
     */
    @Override
    public XDOM getXDOM()
    {
        if (this.content.xdom == null) {
            this.content.xdom = parseContentNoException();
        }

        return this.content.xdom.clone();
    }

    @Override
    public XDOM getPreparedXDOM()
    {
        LocalDateTime xdomPrepareDate = this.content.prepareDate;
        XDOM xdom = this.content.xdom;

        // If the content is prepared and it's allowed to use the cache, return it
        if (xdomPrepareDate != null) {
            if (getCacheControl().isCacheReadAllowed(xdomPrepareDate)) {
                return xdom.clone();
            }

            // Start from scratch if it's not allowed to reuse the already prepared XDOM
            xdom = null;
        }

        // Parse the content if not already done
        if (xdom == null) {
            xdom = parseContentNoException();
        } else {
            // Clone the XDOM to avoid concurrent modifications during the preparation
            xdom = xdom.clone();
        }

        // Prepare the content
        xdomPrepareDate = LocalDateTime.now();
        getMacroTransformation().prepare(xdom);

        // Update the cached XDOM
        this.content.xdom = xdom;
        this.content.prepareDate = xdomPrepareDate;

        return this.content.xdom.clone();
    }

    /**
     * @return true if the document has a xwiki/1.0 syntax content
     */
    public boolean is10Syntax()
    {
        return is10Syntax(getSyntaxId());
    }

    /**
     * @return true if the document has a xwiki/1.0 syntax content
     */
    public boolean is10Syntax(String syntaxId)
    {
        return Syntax.XWIKI_1_0.toIdString().equalsIgnoreCase(syntaxId);
    }

    private void init(DocumentReference reference)
    {
        // if the passed reference is null consider it points to the default reference
        if (reference == null) {
            setDocumentReference(
                Utils.<Provider<DocumentReference>>getComponent(DocumentReference.TYPE_PROVIDER).get());
        } else {
            setDocumentReference(reference);
        }

        this.updateDate = new Date();
        this.updateDate.setTime((this.updateDate.getTime() / 1000) * 1000);
        this.contentUpdateDate = new Date();
        this.contentUpdateDate.setTime((this.contentUpdateDate.getTime() / 1000) * 1000);
        this.creationDate = new Date();
        this.creationDate.setTime((this.creationDate.getTime() / 1000) * 1000);
        this.content = new Content("", this.content.syntax);
        this.format = "";
        this.locale = Locale.ROOT;
        this.defaultLocale = Locale.ROOT;
        this.customClass = "";
        this.comment = "";

        // Note: As there's no notion of an Empty document we don't set the original document
        // field. Thus getOriginalDocument() may return null.
    }

    private boolean executeValidationScript(XWikiContext context, String validationScript)
    {
        try {
            ContextualAuthorizationManager authorization = Utils.getComponent(ContextualAuthorizationManager.class);
            DocumentReference validationScriptReference =
                getCurrentDocumentReferenceResolver().resolve(validationScript, getDocumentReference());

            // Make sure target document is allowed to execute Groovy
            // TODO: this check should probably be right in XWiki#parseGroovyFromPage
            authorization.checkAccess(Right.PROGRAM, validationScriptReference);

            XWikiValidationInterface validObject =
                (XWikiValidationInterface) context.getWiki().parseGroovyFromPage(validationScript, context);

            return validObject.validateDocument(this, context);
        } catch (Throwable e) {
            XWikiValidationStatus.addExceptionToContext(getFullName(), "", e, context);
            return false;
        }
    }

    /**
     * Convert the passed content from the passed syntax to the passed new syntax.
     *
     * @param content the content to convert
     * @param source the reference to where the content comes from (eg document reference)
     * @param currentSyntaxId the syntax of the current content to convert
     * @param targetSyntax the new syntax after the conversion
     * @return the converted content in the new syntax
     * @throws XWikiException if an exception occurred during the conversion process
     * @since 2.4M2
     */
    private static String performSyntaxConversion(String content, DocumentReference source, Syntax currentSyntaxId,
        Syntax targetSyntax) throws XWikiException
    {
        try {
            XDOM dom = parseContent(currentSyntaxId, content, source);
            return renderXDOM(dom, targetSyntax);
        } catch (Exception e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_RENDERING, XWikiException.ERROR_XWIKI_UNKNOWN,
                "Failed to convert document to syntax [" + targetSyntax + "]", e);
        }
    }

    /**
     * Render provided XDOM into content of the provided syntax identifier.
     *
     * @param content the XDOM content to render
     * @param targetSyntax the syntax identifier of the rendered content
     * @return the rendered content
     * @throws XWikiException if an exception occurred during the rendering process
     */
    protected static String renderXDOM(XDOM content, Syntax targetSyntax) throws XWikiException
    {
        try {
            BlockRenderer renderer = Utils.getComponent(BlockRenderer.class, targetSyntax.toIdString());
            WikiPrinter printer = new DefaultWikiPrinter();
            renderer.render(content, printer);
            return printer.toString();
        } catch (Exception e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_RENDERING, XWikiException.ERROR_XWIKI_UNKNOWN,
                "Failed to render document to syntax [" + targetSyntax + "]", e);
        }
    }

    private XDOM parseContent(String content) throws XWikiException
    {
        return parseContent(getSyntax(), content, getDocumentReference());
    }

    /**
     * @param source the reference to where the content comes from (eg document reference)
     */
    private static XDOM parseContent(Syntax syntax, String content, DocumentReference source) throws XWikiException
    {
        ContentParser parser = Utils.getComponent(ContentParser.class);

        try {
            return parser.parse(content, syntax, source);
        } catch (MissingParserException e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_RENDERING, XWikiException.ERROR_XWIKI_UNKNOWN,
                "Failed to find a parser for syntax [" + syntax.toIdString() + "]", e);
        } catch (ParseException e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_RENDERING, XWikiException.ERROR_XWIKI_UNKNOWN,
                "Failed to parse content of syntax [" + syntax.toIdString() + "]", e);
        }
    }

    /**
     * If there's no parser available for the specified syntax default to the XWiki 2.1 syntax.
     */
    private Syntax getDefaultDocumentSyntax()
    {
        Syntax syntax = Utils.getComponent(CoreConfiguration.class).getDefaultDocumentSyntax();

        if (syntax == null || (!Utils.getComponentManager().hasComponent(Parser.class, syntax.toIdString())
            && !Syntax.XWIKI_2_1.equals(syntax))) {
            LOGGER.warn("Failed to find parser for the default syntax [{}]. Defaulting to xwiki/2.1 syntax.", syntax);
            syntax = Syntax.XWIKI_2_1;
        }

        return syntax;
    }

    /**
     * Backward-compatibility method to use in order to resolve a class reference passed as a String into a
     * DocumentReference proper.
     *
     * @return the resolved class reference but using this document's wiki if the passed String doesn't specify a wiki,
     *         the "XWiki" space if the passed String doesn't specify a space and this document's page if the passed
     *         String doesn't specify a page.
     */
    public DocumentReference resolveClassReference(String documentName)
    {
        DocumentReference defaultReference = new DocumentReference(getDocumentReference().getWikiReference().getName(),
            XWiki.SYSTEM_SPACE, getDocumentReference().getName());
        return getExplicitDocumentReferenceResolver().resolve(documentName, defaultReference);
    }

    /**
     * Transforms a XClass reference relative to this document into an absolute reference.
     */
    private DocumentReference resolveClassReference(EntityReference reference)
    {
        if (reference instanceof DocumentReference) {
            return (DocumentReference) reference;
        } else if (reference instanceof LocalDocumentReference) {
            return new DocumentReference((LocalDocumentReference) reference, getDocumentReference().getWikiReference());
        } else {
            DocumentReference defaultReference =
                new DocumentReference(getDocumentReference().getWikiReference().getName(), XWiki.SYSTEM_SPACE,
                    getDocumentReference().getName());
            return getExplicitReferenceDocumentReferenceResolver().resolve(reference, defaultReference);
        }
    }

    /**
     * Return the reference of the parent document as it stored and passed to
     * {@link #setParentReference(EntityReference)}.
     * <p>
     * You should use {@link #getParentReference()} reference if you want the complete parent reference.
     *
     * @return the relative parent reference
     * @since 2.2.3
     */
    public EntityReference getRelativeParentReference()
    {
        return this.parentReference;
    }

    private BaseObject prepareXObject(EntityReference classReference)
    {
        DocumentReference absoluteClassReference = resolveClassReference(classReference);
        BaseObject bobject = getXObject(absoluteClassReference);
        if (bobject == null) {
            bobject = new BaseObject();
            bobject.setXClassReference(classReference);

            addXObject(bobject);
        }
        bobject.setDocumentReference(getDocumentReference());
        setMetaDataDirty(true);
        return bobject;
    }

    /**
     * Apply a 3 ways merge on the current document based on provided previous and new version of the document.
     * <p>
     * All 3 documents are supposed to have the same document reference and language already since that's what makes
     * them uniques.
     *
     * @param previousDocument the previous version of the document
     * @param newDocument the next version of the document
     * @param configuration the configuration of the merge indicates how to deal with some conflicts use cases, etc.
     * @param context the XWiki context
     * @return a repport of what happen during the merge (errors, etc.)
     * @since 3.2M1
     * @deprecated use
     *             {@link MergeManager#mergeDocument(DocumentModelBridge, DocumentModelBridge, DocumentModelBridge, MergeConfiguration)}
     *             instead
     */
    @Deprecated(since = "11.8RC1")
    public MergeResult merge(XWikiDocument previousDocument, XWikiDocument newDocument,
        MergeConfiguration configuration, XWikiContext context)
    {
        MergeManager mergeManager = Utils.getComponent(MergeManager.class);
        MergeDocumentResult mergeDocumentResult =
            mergeManager.mergeDocument(previousDocument, newDocument, this, configuration);

        MergeResult mergeResult = new MergeResult();
        mergeResult.getLog().addAll(mergeDocumentResult.getLog());
        mergeResult.setModified(mergeResult.isModified() || mergeDocumentResult.isModified());

        if (!configuration.isProvidedVersionsModifiables()) {
            this.apply((XWikiDocument) mergeDocumentResult.getMergeResult());
        }

        return mergeResult;
    }

    /**
     * Apply modification coming from provided document.
     * <p>
     * Thid method does not take into account versions and author related informations and the provided document should
     * have the same reference. Like {@link #merge(XWikiDocument, XWikiDocument, MergeConfiguration, XWikiContext)},
     * this method is dealing with "real" data and should not change anything related to version management and document
     * identifier.
     * <p>
     * Important note: this method does not take care of attachments contents related operations and only remove
     * attachments which need to be removed from the list. For memory handling reasons all attachments contents related
     * operations should be done elsewhere.
     *
     * @param document the document to apply
     * @return false is nothing changed
     */
    public boolean apply(XWikiDocument document)
    {
        return apply(document, true);
    }

    /**
     * Apply modification coming from provided document.
     * <p>
     * Thid method does not take into account versions and author related informations and the provided document should
     * have the same reference. Like {@link #merge(XWikiDocument, XWikiDocument, MergeConfiguration, XWikiContext)},
     * this method is dealing with "real" data and should not change everything related to version management and
     * document identifier.
     *
     * @param document the document to apply
     * @return false is nothing changed
     */
    public boolean apply(XWikiDocument document, boolean clean)
    {
        boolean modified = false;

        // /////////////////////////////////
        // Document

        if (!Strings.CS.equals(getContent(), document.getContent())) {
            setContent(document.getContent());
            modified = true;
        }
        if (ObjectUtils.notEqual(getSyntax(), document.getSyntax())) {
            setSyntax(document.getSyntax());
            modified = true;
        }

        if (ObjectUtils.notEqual(getDefaultLocale(), document.getDefaultLocale())) {
            setDefaultLocale(document.getDefaultLocale());
            modified = true;
        }

        if (!Strings.CS.equals(getTitle(), document.getTitle())) {
            setTitle(document.getTitle());
            modified = true;
        }

        if (!Strings.CS.equals(getDefaultTemplate(), document.getDefaultTemplate())) {
            setDefaultTemplate(document.getDefaultTemplate());
            modified = true;
        }
        if (ObjectUtils.notEqual(getRelativeParentReference(), document.getRelativeParentReference())) {
            setParentReference(document.getRelativeParentReference());
            modified = true;
        }

        if (!Strings.CS.equals(getCustomClass(), document.getCustomClass())) {
            setCustomClass(document.getCustomClass());
            modified = true;
        }

        if (!Strings.CS.equals(getValidationScript(), document.getValidationScript())) {
            setValidationScript(document.getValidationScript());
            modified = true;
        }

        if (isHidden() != document.isHidden()) {
            setHidden(document.isHidden());
            modified = true;
        }

        if (isEnforceRequiredRights() != document.isEnforceRequiredRights()) {
            setEnforceRequiredRights(document.isEnforceRequiredRights());
            modified = true;
        }

        // /////////////////////////////////
        // XObjects

        if (clean) {
            // Delete objects that don't exist anymore
            for (List<BaseObject> objects : getXObjects().values()) {
                // Duplicate the list since we are potentially going to modify it
                for (BaseObject originalObj : new ArrayList<BaseObject>(objects)) {
                    if (originalObj != null) {
                        BaseObject newObj =
                            document.getXObject(originalObj.getXClassReference(), originalObj.getNumber());
                        if (newObj == null) {
                            // The object was deleted
                            removeXObject(originalObj);
                            modified = true;
                        }
                    }
                }
            }
        }
        // Add new objects or update existing objects
        for (List<BaseObject> objects : document.getXObjects().values()) {
            for (BaseObject newObj : objects) {
                if (newObj != null) {
                    BaseObject originalObj = getXObject(newObj.getXClassReference(), newObj.getNumber());
                    if (originalObj == null) {
                        // The object added or modified
                        setXObject(newObj.getNumber(), newObj);
                        modified = true;
                    } else {
                        // The object added or modified
                        modified |= originalObj.apply(newObj, clean);
                    }
                }
            }
        }

        // /////////////////////////////////
        // XClass

        modified |= getXClass().apply(document.getXClass(), clean);
        if (ObjectUtils.notEqual(getXClassXML(), document.getXClassXML())) {
            setXClassXML(document.getXClassXML());
            modified = true;
        }

        // /////////////////////////////////
        // Attachments

        if (clean) {
            // Delete attachments that don't exist anymore
            for (XWikiAttachment attachment : new ArrayList<XWikiAttachment>(getAttachmentList())) {
                if (document.getAttachment(attachment.getFilename()) == null) {
                    removeAttachment(attachment);
                }
            }
        }
        // Add new attachments or update existing attachments
        for (XWikiAttachment attachment : document.getAttachmentList()) {
            XWikiAttachment originalAttachment = getAttachment(attachment.getFilename());
            if (originalAttachment == null) {
                addAttachment(attachment);
            } else {
                originalAttachment.apply(attachment);
            }
        }

        return modified;
    }

    private XWikiAttachmentStoreInterface resolveXWikiAttachmentStoreInterface(String storeType, XWikiContext xcontext)
    {
        XWikiAttachmentStoreInterface store = getXWikiAttachmentStoreInterface(storeType);

        if (store != null) {
            return store;
        }

        return xcontext.getWiki().getDefaultAttachmentContentStore();
    }

    private XWikiAttachmentStoreInterface getXWikiAttachmentStoreInterface(String storeType)
    {
        if (storeType != null && !storeType.equals(XWikiHibernateAttachmentStore.HINT)) {
            try {
                return Utils.getContextComponentManager().getInstance(XWikiAttachmentStoreInterface.class, storeType);
            } catch (ComponentLookupException e) {
                LOGGER.warn("Can't find attachment content store for type [{}]", storeType, e);
            }
        }

        return null;
    }

    /**
     * Compute and return the maximum authorized length for the full name (i.e. the serialized reference of the
     * document) based on the current store limitation.
     *
     * @return the maximum authorized length for a document full name.
     * @since 11.4RC1
     */
    public int getLocalReferenceMaxLength()
    {
        return getStore().getLimitSize(this.getXWikiContext(), this.getClass(), "fullName");
    }

    @Override
    public DocumentAuthors getAuthors()
    {
        return this.authors;
    }

    /**
     * Update the author of the document. It's the recommended way to update it if you don't fully understand the
     * various types of authors exposed by {@link #getAuthor()}.
     * <p>
     * What will happen in practice is the following:
     * <ul>
     * <li>the effective and original metadata authors are set to the passed reference</li>
     * <li>when saving the document, if the content is modified (the content dirty flag is true) then the content author
     * will also be updated to the passed reference</li>
     * </ul>
     * 
     * @param userReference the reference of the new author of the document
     * @since 16.1.0RC1
     */
    @Unstable
    public void setAuthor(UserReference userReference)
    {
        getAuthors().setEffectiveMetadataAuthor(userReference);
        getAuthors().setOriginalMetadataAuthor(userReference);
    }

    /**
     * This getter has been created for hibernate in order to properly fill the DB field, it's not meant to be used for
     * other purpose. For getting the displayed author, rely on {@link #getAuthors()}.
     *
     * @return the serialization of the displayed author reference.
     */
    private String getOriginalMetadataAuthorReference()
    {
        if (this.getAuthors().getOriginalMetadataAuthor() == null
            || this.getAuthors().getOriginalMetadataAuthor() == GuestUserReference.INSTANCE) {
            return "";
        } else {
            return userReferenceToString(this.getAuthors().getOriginalMetadataAuthor());
        }
    }

    /**
     * This setter has been created for hibernate in order to properly create the XWikiDocument instance with the
     * displayed author set, it's not meant to be used for other purpose.
     *
     * @param serializedUserReference the serialization of the displayed author reference.
     */
    private void setOriginalMetadataAuthorReference(String serializedUserReference)
    {
        if (!StringUtils.isEmpty(serializedUserReference)) {
            UserReference userReference = userStringToUserReference(serializedUserReference);
            this.authors.setOriginalMetadataAuthor(userReference);
        }
    }

    /**
     * Make sure any document metadata which may depend on configuration is initialized to its default value.
     * 
     * @since 14.8RC1
     * @since 14.4.4
     * @since 13.10.10
     */
    public void initialize()
    {
        // There is no syntax by default in a new document and the default one is retrieved from the configuration
        setSyntax(getSyntax());
    }

    /**
     * @return if rendering transformations shall be executed in restricted mode and the title not be executed
     * @since 14.10.7
     * @since 15.2RC1
     */
    @Override
    public boolean isRestricted()
    {
        return this.restricted;
    }

    /**
     * Set the restricted property that disables scripts and other dangerous content.
     * <p>
     * This property is not stored in the database as it is only supposed to be {@code true} on documents that do not
     * correspond to the current version of the document.
     *
     * @param restricted if rendering transformations shall be executed in restricted mode and the title not be executed
     * @since 14.10.7
     * @since 15.2RC1
     */
    public void setRestricted(boolean restricted)
    {
        this.restricted = restricted;
    }
}
