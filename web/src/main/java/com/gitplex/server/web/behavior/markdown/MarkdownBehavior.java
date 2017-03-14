package com.gitplex.server.web.behavior.markdown;

import static org.apache.wicket.ajax.attributes.CallbackParameter.explicit;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes.Method;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.util.crypt.Base64;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitplex.launcher.loader.AppLoader;
import com.gitplex.server.manager.MarkdownManager;
import com.gitplex.server.web.behavior.AbstractPostAjaxBehavior;
import com.gitplex.server.web.behavior.markdown.emoji.EmojiOnes;
import com.gitplex.server.web.page.base.BasePage;
import com.google.common.base.Charsets;

@SuppressWarnings("serial")
public class MarkdownBehavior extends AbstractPostAjaxBehavior {

	protected static final int ATWHO_LIMIT = 5;
	
	@Override
	protected void respond(AjaxRequestTarget target) {
		IRequestParameters params = RequestCycle.get().getRequest().getPostParameters();
		String type = params.getParameterValue("type").toString();
		
		if (type.equals("markdownPreview")) {
			String markdown = params.getParameterValue("param").toOptionalString();
			String preview;
			if (StringUtils.isNotBlank(markdown)) {
				preview = AppLoader.getInstance(MarkdownManager.class).render(markdown, true);
			} else { 
				preview = "<i>Nothing to preview.</i>";
			}
			String script = String.format(""
					+ "var $preview=$('#%s~.md-preview');"
					+ "$preview.html('%s');"
					+ "gitplex.server.markdown.initRendered('%s');",
					getComponent().getMarkupId(), 
					StringEscapeUtils.escapeEcmaScript(preview), 
					getComponent().getMarkupId());
			target.appendJavaScript(script);
		} else if (type.equals("emojiQuery")){
			List<String> emojiNames = new ArrayList<>();
			String emojiQuery = params.getParameterValue("param").toOptionalString();
			if (StringUtils.isNotBlank(emojiQuery)) {
				emojiQuery = emojiQuery.toLowerCase();
				for (String emojiName: EmojiOnes.getInstance().all().keySet()) {
					if (emojiName.toLowerCase().contains(emojiQuery))
						emojiNames.add(emojiName);
				}
				emojiNames.sort((name1, name2) -> name1.length() - name2.length());
			} else {
				emojiNames.add("smile");
				emojiNames.add("worried");
				emojiNames.add("blush");
				emojiNames.add("+1");
				emojiNames.add("-1");
			}

			List<Map<String, String>> emojis = new ArrayList<>();
			for (String emojiName: emojiNames) {
				if (emojis.size() < ATWHO_LIMIT) {
					String emojiCode = EmojiOnes.getInstance().all().get(emojiName);
					CharSequence url = RequestCycle.get().urlFor(new PackageResourceReference(
							EmojiOnes.class, "icon/" + emojiCode + ".png"), new PageParameters());
					Map<String, String> emoji = new HashMap<>();
					emoji.put("name", emojiName);
					emoji.put("url", url.toString());
					emojis.add(emoji);
				}
			}
			String json;
			try {
				json = AppLoader.getInstance(ObjectMapper.class).writeValueAsString(emojis);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
			String script = String.format("$('#%s').data('atWhoEmojiRenderCallback')(%s);",
					getComponent().getMarkupId(), json);
			target.appendJavaScript(script);
		} else if (type.equals("loadEmojis")) {
			List<Map<String, String>> emojis = new ArrayList<>();
			for (Map.Entry<String, String> entry: EmojiOnes.getInstance().all().entrySet()) {
				Map<String, String> emoji = new HashMap<>();
				emoji.put("name", entry.getKey());
				emoji.put("url", RequestCycle.get().urlFor(new PackageResourceReference(
						EmojiOnes.class, "icon/" + entry.getValue() + ".png"), new PageParameters()).toString());
				emojis.add(emoji);
			}

			String json;
			try {
				json = AppLoader.getInstance(ObjectMapper.class).writeValueAsString(emojis);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}

			String script = String.format("gitplex.server.markdown.onEmojisLoaded('%s', %s);", 
					getComponent().getMarkupId(), json);
			target.appendJavaScript(script);
		} else if (type.equals("selectImage") || type.equals("selectLink")) {
			BasePage page = (BasePage) getComponent().getPage();
			SelectUrlPanel urlSelector = new SelectUrlPanel(
					page.getRootComponents().newChildId(), this, type.equals("selectImage"));
			urlSelector.setOutputMarkupId(true);
			page.getRootComponents().add(urlSelector);
			urlSelector.setMarkupId(getComponent().getMarkupId() + "-urlselector");
			target.add(urlSelector);
		} else if (type.equals("insertUrl")) {
			String name;
			try {
				name = URLDecoder.decode(params.getParameterValue("param").toString(), Charsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			String replaceMessage = params.getParameterValue("param2").toString();
			String url = getAttachmentSupport().getAttachmentUrl(name);
			insertUrl(target, isWebSafeImage(name), url, name, replaceMessage);
		} else {
			throw new IllegalStateException("Unknown callback type: " + type);
		}
	}
	
	public void insertUrl(AjaxRequestTarget target, boolean isImage, String url, 
			@Nullable String name, String replaceMessage) {
		String script = String.format("gitplex.server.markdown.insertUrl('%s', %s, '%s', %s, %s);",
				getComponent().getMarkupId(), isImage, StringEscapeUtils.escapeEcmaScript(url), 
				name!=null?"'"+StringEscapeUtils.escapeEcmaScript(name)+"'":"undefined", 
				replaceMessage!=null?"'"+replaceMessage+"'":"undefined");
		target.appendJavaScript(script);
	}
	
	public void closeUrlSelector(AjaxRequestTarget target, Component urlSelector) {
		BasePage page = (BasePage) urlSelector.getPage();
		page.getRootComponents().remove(urlSelector);
		String script = String.format("$('#%s-urlselector').closest('.modal').modal('hide');", 
				getComponent().getMarkupId());
		target.appendJavaScript(script);
	}
	
	public boolean isWebSafeImage(String fileName) {
		fileName = fileName.toLowerCase();
		return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") 
				|| fileName.endsWith(".gif") || fileName.endsWith(".png");
	}
	
	@Override
	protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
		super.updateAjaxAttributes(attributes);
		
		attributes.setMethod(Method.POST);
	}

	@Override
	public void renderHead(Component component, IHeaderResponse response) {
		super.renderHead(component, response);
		
		response.render(JavaScriptHeaderItem.forReference(new MarkdownResourceReference()));
		
		String encodedAttachmentSupport;
		AttachmentSupport attachmentSupport = getAttachmentSupport();
		if (attachmentSupport != null) {
			encodedAttachmentSupport = Base64.encodeBase64String(SerializationUtils.serialize(attachmentSupport));
			encodedAttachmentSupport = StringUtils.deleteWhitespace(encodedAttachmentSupport);
			encodedAttachmentSupport = StringEscapeUtils.escapeEcmaScript(encodedAttachmentSupport);
			encodedAttachmentSupport = "'" + encodedAttachmentSupport + "'";
		} else {
			encodedAttachmentSupport = "undefined";
		}
		String script = String.format("gitplex.server.markdown.onDomReady('%s', %s, %s, %s, %d);", 
				component.getMarkupId(true), 
				ATWHO_LIMIT,
				getCallbackFunction(explicit("type"), explicit("param"), explicit("param2")), 
				encodedAttachmentSupport, 
				attachmentSupport!=null?attachmentSupport.getAttachmentMaxSize():0);
		response.render(OnDomReadyHeaderItem.forScript(script));
	}

	@Nullable
	public AttachmentSupport getAttachmentSupport() {
		return null;
	}

}
