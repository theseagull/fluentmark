/*******************************************************************************
 * Copyright (c) 2016 - 2017 Certiv Analytics and others. All rights reserved. This program and the
 * accompanying materials are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package net.certiv.fluentmark.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.markdownj.MarkdownProcessor;
import org.pegdown.PegDownProcessor;

import com.github.rjeschke.txtmark.BlockEmitter;
import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Configuration.Builder;
import com.github.rjeschke.txtmark.Processor;

import net.certiv.fluentmark.FluentUI;
import net.certiv.fluentmark.preferences.Prefs;
import net.certiv.fluentmark.preferences.pages.PrefPageEditor;
import net.certiv.fluentmark.util.Cmd;

public class Converter {

	private static final Pattern TICS = Pattern.compile("(```+\\s*dot\\s+)(.*?)(```+)", Pattern.DOTALL);
	private static final Pattern TLDS = Pattern.compile("(~~~+\\s*dot\\s+)(.*?)(~~~+)", Pattern.DOTALL);
	private static final BlockEmitter emitter = new DotCodeBlockEmitter();
	private IPreferenceStore store;

	public Converter() {
		super();
		store = FluentUI.getDefault().getPreferenceStore();
	}

	public boolean useMathJax() {
		switch (store.getString(Prefs.EDITOR_MD_CONVERTER)) {
			case Prefs.KEY_PANDOC:
				return store.getBoolean(Prefs.EDITOR_PANDOC_MATHJAX);
			default:
				return false;
		}
	}

	public String convert(String base, String text) {
		switch (store.getString(Prefs.EDITOR_MD_CONVERTER)) {
			case Prefs.KEY_BLACKFRIDAY:
				return useBlackFriday(base, text);
			case Prefs.KEY_MARDOWNJ:
				return useMarkDownJ(base, text);
			case Prefs.KEY_PEGDOWN:
				return usePegDown(base, text);
			case Prefs.KEY_COMMONMARK:
				return useCommonMark(base, text);
			case Prefs.KEY_TXTMARK:
				return useTxtMark(base, text);
			case Prefs.KEY_PANDOC:
				return usePandoc(base, text);
			case Prefs.EDITOR_EXTERNAL_COMMAND:
				return useExternalCli(base, text);
		}
		return "";
	}

	// Use MarkdownJ
	private String useMarkDownJ(String base, String text) {
		MarkdownProcessor markdown = new MarkdownProcessor();
		return markdown.markdown(text);
	}

	// Use PegDown
	private String usePegDown(String base, String text) {
		PegDownProcessor pegdown = new PegDownProcessor();
		return pegdown.markdownToHtml(text);
	}

	// Use CommonMark
	private String useCommonMark(String base, String text) {
		Parser parser = Parser.builder().build();
		Node document = parser.parse(text);
		HtmlRenderer renderer = HtmlRenderer.builder().build();
		return renderer.render(document);
	}

	// Use TxtMark
	private String useTxtMark(String base, String text) {
		boolean safeMode = store.getBoolean(Prefs.EDITOR_TXTMARK_SAFEMODE);
		boolean extended = store.getBoolean(Prefs.EDITOR_TXTMARK_EXTENDED);
		boolean dotMode = store.getBoolean(Prefs.EDITOR_DOTMODE_ENABLED);

		Builder builder = Configuration.builder();
		if (safeMode) builder.enableSafeMode();
		if (extended || dotMode) builder.forceExtentedProfile();
		if (dotMode) builder.setCodeBlockEmitter(emitter);
		Configuration config = builder.build();
		return Processor.process(text, config);
	}

	// Use Pandoc
	private String usePandoc(String base, String text) {
		String cmd = store.getString(Prefs.EDITOR_PANDOC_PROGRAM);
		if (cmd.trim().isEmpty()) return "";

		List<String> args = new ArrayList<>();
		args.add(cmd);
		args.add("--no-highlight"); // use highlightjs instead
		if (store.getBoolean(Prefs.EDITOR_PANDOC_ADDTOC)) args.add("--toc");
		if (store.getBoolean(Prefs.EDITOR_PANDOC_MATHJAX)) args.add("--mathjax");

		if (!store.getBoolean(Prefs.EDITOR_PANDOC_SMART)) {
			args.add("-f");
			args.add("markdown-smart");
		}

		if (store.getBoolean(Prefs.EDITOR_DOTMODE_ENABLED)) {
			text = preprocess(text);
		}
		return Cmd.process(args.toArray(new String[args.size()]), base, text);
	}

	// Use BlackFriday
	private String useBlackFriday(String base, String text) {
		String cmd = store.getString(Prefs.EDITOR_BLACKFRIDAY_PROGRAM);
		if (cmd.trim().isEmpty()) return "";

		List<String> args = new ArrayList<>();
		args.add(cmd);
		if (store.getBoolean(Prefs.EDITOR_BLACKFRIDAY_ADDTOC)) {
			args.add("-toc");
		}
		if (store.getBoolean(Prefs.EDITOR_BLACKFRIDAY_SMART)) {
			args.add("-smartypants");
			args.add("-fractions");
		}

		if (store.getBoolean(Prefs.EDITOR_DOTMODE_ENABLED)) {
			text = preprocess(text);
		}
		return Cmd.process(args.toArray(new String[args.size()]), base, text);
	}

	private String preprocess(String text) {
		text = preprocess(TICS, text);
		return preprocess(TLDS, text);
	}

	private String preprocess(Pattern p, String text) {
		Matcher m = p.matcher(text);
		int mark = 0;
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			int cnt = m.groupCount();
			for (int idx = 1; idx <= cnt; idx++) {
				sb.append(text.substring(mark, m.start(idx)));
				idx++;
				String dotText = text.substring(m.start(idx), m.end(idx));
				sb.append(DotGen.runDot(dotText));
				idx++;
				mark = m.end(idx);
			}
		}
		if (mark < text.length()) sb.append(text.substring(mark));
		return sb.toString();
	}

	// Use external command
	private String useExternalCli(String base, String text) {
		String cmd = store.getString(PrefPageEditor.EDITOR_EXTERNAL_COMMAND);
		if (cmd.trim().isEmpty()) {
			return "Specify an external markdown converter command in preferences.";
		}

		String[] args = Cmd.parse(cmd);
		if (args.length > 0) {
			return Cmd.process(args, base, text);
		}
		return "";
	}
}
