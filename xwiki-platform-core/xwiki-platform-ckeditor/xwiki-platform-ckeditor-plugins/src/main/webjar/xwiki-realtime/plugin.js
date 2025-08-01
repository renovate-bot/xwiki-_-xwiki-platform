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
(function() {
  'use strict';
  var $ = jQuery;

  // Declare the configuration namespace.
  CKEDITOR.config['xwiki-realtime'] = CKEDITOR.config['xwiki-realtime'] || {
    __namespace: true
  };

  CKEDITOR.plugins.add('xwiki-realtime', {
    requires: 'notification,xwiki-loading',

    init : function(editor) {
      applyStyleSheets(editor);

      if (editor.elementMode === CKEDITOR.ELEMENT_MODE_INLINE) {
        // When editing in-place we need to maximize the parent of the editable area in order to have the user caret
        // indicators visible (they are injected after the editable area).
        editor.element.getParent().addClass('cke_editable_fullscreen');
      }

      // CKEditor's HTML parser doesn't preserve the space character typed at the end of a line of text. For instance,
      // the following:
      //   CKEDITOR.htmlParser.fragment.fromHtml('<p>x </p>')
      // is parsed as:
      //   <p>x</p>
      // Normally browsers should insert a non-breaking space when the user types a space at the end of a line. Chrome
      // behaves like this. Firefox inserts a normal space character instead. We need to fix this otherwise that space
      // character is lost when the content is saved or when the user switches to Source. This is especially critical
      // for the realtime editing where the content you type is merged with the content typed by other users and fed
      // back to your editor. So if you type at the end of a paragraph while remote changes are received you don't want
      // the space between the words you type to be lost.
      preserveSpaceCharAtTheEndOfLine(editor);

      editor.delayInstanceReady(new Promise((resolve, reject) => {
        require([
          'xwiki-realtime-loader',
          'xwiki-ckeditor-realtime-adapter',
          'xwiki-realtime-interface'
        ], asyncRequireCallback(async (Loader, Adapter, Interface) => {
          await enableRealtimeEditing(editor, Loader, Adapter);

          // The edited (HTML) content is normalized when the realtime editing is enabled (e.g. by adding some BR
          // elements to ensure the HTML is the same across different browsers) which makes the editor dirty, although
          // there aren't any real content changes (that would be noticed in the source wiki syntax). We reset the
          // dirty state in order to avoid getting the leave confirmation when leaving the editor just after it was
          // loaded.
          editor.resetDirty();

          editor._realtimeInterface = Interface;
          editor._realtimeSource = {
            // True if the editor was in the realtime session before switching to source.
            realtime: false,

            // The value of editor.checkDirty() before switching to source.
            dirty: false,

            // The result of editor.getSnapshot() right after switching to source.
            previousValue: null
          };
          editor.on('beforeModeUnload', this.onBeforeModeUnload.bind(this));
          CKEDITOR.plugins.xwikiSource?.addModeChangeHandler(editor, this.onModeChanged.bind(this), 10);
          editor.on('modeReady', this.onModeReady.bind(this));
        }, resolve, reject), reject);
      }));
    },

    onBeforeModeUnload: function(event) {
      const editor = event.editor;
      if (editor.mode === 'wysiwyg') {
        // Store the dirty state before leaving the WYSIWYG mode.
        editor._realtimeSource.dirty = editor.checkDirty();
      } else if (editor.mode === 'source') {
        // Update the dirty state before leaving the Source mode because we can join the realtime session when switching
        // to WYSIWYG mode only if the content is not dirty (or there is no one else in the editing session). The
        // content is dirty if there are unsaved changes made before switching to Source mode or while in Source mode.
        editor._realtimeSource.dirty = editor._realtimeSource.dirty ||
          (editor._realtimeSource.previousValue !== editor.getSnapshot());
      }
    },

    onModeChanged: async function(editor, {previousMode}) {
      const realtimeCheckbox = editor._realtimeInterface.getAllowRealtimeCheckbox();

      // This handles the switching between wysiwyg and source mode. The switch between wysiwyg and source modes marks
      // the editor as dirty. But we would like to rely on the dirty state of the editor to decide wether or not to
      // re-join the realtime session after editing the source. To determine wether or not the editor is dirty, CKEditor
      // compares the current snapshot of the content to a snapshot saved during a editor.resetDirty() call. We need to
      // keep track of the dirty state ourselves by saving a snapshot.

      // When switching back to wysiwyg, even without editing the source, the content can be marked as dirty, making
      // switching back and forth between wysiwyg and source leave the realtime session permanently even when no changes
      // were made. To prevent that, we reset the dirty state when the realtime-tracked dirty state is clean.

      // We use a mode change handler to capture the dirty state prior to the mode change, and abort the realtime
      // session before the iframe (when in framed wysiwyg) is destroyed. We use the modeReady event to restore the
      // dirty state once the mode change is done. and re-join the realtime session if suitable.

      // We keep track of the realtime status before switching to source mode in the editor._realtimeSource attribute.

      if (previousMode === 'wysiwyg' && editor.mode === 'source') {
        // Switching from WYSIWYG to Source mode.

        // Store the realtime state before switching to Source mode in order to restore the state when switching back to
        // WYSIWYG mode.
        editor._realtimeSource.realtime = realtimeCheckbox.prop('checked');

        // When using the iframed editor, switching to source destroys the iframe, preventing the realtime framework
        // from applying new patches. We need to leave the realtime session when switching to source mode in order to
        // avoid unexpected behaviour.
        if (editor._realtimeSource.realtime) {
          await this.onSourceFromWYSIWYG(editor, realtimeCheckbox);
        }
      } else if (previousMode === 'source' && editor.mode === 'wysiwyg' && editor._realtimeSource.realtime) {
        // Swithing from Source back to WYSIWYG mode, which had realtime enabled before the switch.
        await this.onWYSIWYGFromSource(editor, realtimeCheckbox);
      }
    },

    onSourceFromWYSIWYG: async function(editor, realtimeCheckbox) {
      // Abort the realtime session. This operation is asynchronous because we want to push uncommitted work before
      // disconnecting.
      await editor._realtime._onAbort();

      // Show the user that we left the realtime session.
      realtimeCheckbox.prop('checked', false);

      // Show a notification explaining that we temporarily left the realtime session.
      editor.showNotification(editor.localization.get(
        'xwiki-realtime.notification.sourcearea.temporarilyLeftSession'));
    },

    onWYSIWYGFromSource: async function(editor, realtimeCheckbox) {
      // Update the realtime channels to prepare joining the realtime session, as well as knowing if there are users
      // in the session.
      await editor._realtime._updateChannels();

      let translationKey = 'xwiki-realtime.notification.sourcearea.rejoiningSession.noChanges';
      let rejoin = true;

      // When the editor is dirty, we can join only if we are alone.
      if (editor._realtimeSource.dirty) {
        /*jshint -W106 */
        if (editor._realtime._realtimeContext.channels.wysiwyg_users > 0) {
          /*jshint +W106 */

          translationKey = 'xwiki-realtime.notification.sourcearea.notRejoiningSession';
          rejoin = false;
        } else {
          translationKey = 'xwiki-realtime.notification.sourcearea.rejoiningSession.alone';
        }
      }

      editor.showNotification(
        editor.localization.get(translationKey),
        rejoin ? 'success' : 'warning',
        rejoin ? undefined : 5000
      );
      if (rejoin) {
        realtimeCheckbox.prop('checked', true);
        editor._realtime._startRealtimeSync();
      }
    },

    onModeReady: function(event) {
      const editor = event.editor;

      if (editor.mode === 'source') {
        // Once the mode switch is done, we store a snapshot of the source allowing to check if changes were made when
        // switching back to WYSIWYG.
        editor._realtimeSource.previousValue = editor.getSnapshot();
      } else if (editor.mode === 'wysiwyg' && !editor._realtimeSource.dirty) {
        // There are no unsaved changes. But the editor might consider itself dirty because of the mode change.
        editor.resetDirty();
      }

      // The user should not be able to join the realtime editing session while in source mode. We disable the allow
      // realtime checkbox while in source mode, and enable it when we go back to wysiwyg.
      const realtimeCheckbox = editor._realtimeInterface.getAllowRealtimeCheckbox();
      realtimeCheckbox.prop('disabled', editor.mode !== 'wysiwyg');
    }
  });

  function applyStyleSheets(editor) {
    const styleSheets = editor.config['xwiki-realtime'].stylesheets;

    // Add the stylesheet to the page where the editor is used (e.g. for the realtime toolbar).
    addStyleSheets(styleSheets, document);

    // Add the stylesheet also to the edited content, in case the edited content is inside an iframe, for the user
    // caret indicators. We have to do this each time the iframe is reloaded (e.g. after a macro is inserted).
    // Note that we can't simply use the editor#addContentsCss method because we have the fullPage configuration on.
    editor.on('contentDom', () => {
      addStyleSheets(styleSheets, editor.document.$);
    });
    if (editor.document && editor.document.$ !== document) {
      // Initial content load.
      addStyleSheets(styleSheets, editor.document.$);
    }
  }

  function addStyleSheets(urls, doc) {
    urls.forEach(url => {
      $('<link/>', doc).attr({
        type: 'text/css',
        rel: 'stylesheet',
        href: url
      }).appendTo(doc.head);
    });
  }

  function preserveSpaceCharAtTheEndOfLine(editor) {
    editor.on('beforeGetData', () => {
      const range = !editor.isDetached() && editor.getSelection()?.getRanges()[0];
      const textNode = range?.startContainer;
      // Check if the caret is at the end of a text node that ends with a space and is followed by a line break.
      if (editor.mode === 'wysiwyg' && range?.collapsed && textNode.type === CKEDITOR.NODE_TEXT &&
          range.startOffset === textNode.getLength() && textNode.getText().endsWith(' ') &&
          isFollowedByLineBreak(textNode)) {
        fixTrailingSpace(textNode);
        // Make sure the caret remains at the end of the text node after we modify its data.
        range.setStart(textNode, textNode.getLength());
        range.select();
      }
    // Note that we use a high priority (0) to ensure that our listener is called before the HTML parser.
    }, null, null, 0);
  }

  function isFollowedByLineBreak(textNode) {
    // Is directly inside a block element...
    return CKEDITOR.dtd.$block[textNode.getParent().getName()] &&
      // ...and is either the last child or followed by a block element or a BR element.
      (!textNode.getNext() || CKEDITOR.dtd.$block[textNode.getNext().getName?.()] ||
        textNode.getNext().getName?.() === 'br');
  }

  function fixTrailingSpace(textNode) {
    // We replicate here the behavior of Chrome when multiple space characters are typed.
    const whitespaceLength = textNode.getLength() - textNode.getText().trimEnd().length;
    const whitespaceSuffix = '\u00A0 '.repeat((whitespaceLength - 1) / 2) +
      '\u00A0'.repeat((whitespaceLength - 1) % 2 + 1);
    textNode.$.replaceData(textNode.getLength() - whitespaceLength, whitespaceLength, whitespaceSuffix);
  }

  async function enableRealtimeEditing(editor, Loader, Adapter) {
    const info = {
      type: 'wysiwyg',
      field: editor.name,
      // This is used to generate the WYSIWYG editor URL so we need to take into account if we are in view mode
      // (i.e. in-place editing) or in edit mode (standalone editing).
      href: window.XWiki?.contextaction === 'view' ? '&force=1' : '&editor=wysiwyg&force=1',
      name: 'WYSIWYG',
      compatible: ['wysiwyg', 'wiki']
    };

    const realtimeContext = await Loader.bootstrap(info);
    await new Promise((resolve, reject) => {
      require(['xwiki-realtime-wysiwyg'], asyncRequireCallback(RealtimeWysiwygEditor => {
        editor._realtime = new RealtimeWysiwygEditor(new Adapter(editor, CKEDITOR), realtimeContext);
      }, resolve, reject), reject);
    });
  }

  /**
   * This covers two problems:
   *   1. the error callback passed to require() is not called when there is an exception in the success callback
   *   2. the success callback is not called if marked as async
   */
  function asyncRequireCallback(asyncCallback, resolve, reject) {
    return (...args) => {
      Promise.try(asyncCallback, ...args).then(resolve).catch(reject);
    };
  }

  // Add support for synchronizing the upload widgets when realtime editing is enabled.
  const originalAddUploadWidget = CKEDITOR.fileTools.addUploadWidget;
  CKEDITOR.fileTools.addUploadWidget = function(editor, widgetName, ...args) {
    if (editor.plugins['xwiki-realtime']) {
      const handler = editor.on('widgetDefinition', function(event) {
        const widgetDefinition = event.data;
        if (widgetDefinition.name === widgetName) {
          handler.removeListener();

          const originalInit = widgetDefinition.init;
          Object.assign(widgetDefinition, {
            /**
             * Upcast upload widgets coming from other users editing at the same content in realtime. These widgets are
             * temporary placeholders that are going to be replaced with the actual content when the upload is complete.
             *
             * @param {CKEDITOR.htmlParser.element} element the element to check if it can be upcasted to this upload
             *   widget
             */
            upcast: function(element) {
              return element.hasClass?.('xwiki-widget-placeholder-' + this.name);
            },

            init: function(...args) {
              const widget = this;
              // Call the original init method only if this is a real upload widget and not a placeholder.
              if (widget.wrapper.findOne('[data-cke-upload-id]')) {
                originalInit.call(widget, ...args);
              }
            },

            /**
             * Upload widgets are by default downcasted to an empty text node because they are temporary placeholders
             * that are not supposed to be saved. They are replaced with the actual content when the upload is complete.
             *
             * The realtime editor synchronizes the output HTML of the editor which by default doesn't include the
             * upload widgets (because they are not supposed to be saved). This creates a problem when a remote change
             * is received while an upload is in progress: the remote content doesn't include the upload widget so the
             * upload widget gets removed when we compute the diff between the local content and the remote content and
             * then apply the patch.
             *
             * In order to overcome this we have to include the upload widgets in the output HTML of the editor, when
             * this HTML is used for realtime synchronization.
             *
             * @param {CKEDITOR.htmlParser.element} widgetElementClone a clone of the widget element to downcast
             */
            downcast: function(widgetElementClone) {
              const widget = this;
              if (widget.editor.config._includeUploadWidgetsInOutputHTML) {
                const placeholder = new CKEDITOR.htmlParser.element(widget.inline ? 'span' : 'div', {
                  'class': 'xwiki-widget-placeholder-' + widget.name
                });
                // Add a non-breaking space to ensure the placeholder is visible and thus not removed by CKEditor.
                placeholder.add(new CKEDITOR.htmlParser.text('\u00A0'));
                return placeholder;
              } else {
                return new CKEDITOR.htmlParser.text('');
              }
            },
          });

          // Make sure the upload widget placeholders are not saved.
          editor.dataProcessor?.htmlFilter?.addRules({
            '^': function(element) {
              if (element.hasClass?.('xwiki-widget-placeholder-' + widgetName)) {
                return false;
              }
            }
          }, {priority: 5, applyToAll: true});
        }
      });
    }
    return originalAddUploadWidget.call(this, editor, widgetName, ...args);
  };
})();
