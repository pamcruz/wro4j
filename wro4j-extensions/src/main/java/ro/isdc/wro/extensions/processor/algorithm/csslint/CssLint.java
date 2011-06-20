/*
 *  Copyright wro4j@2011.
 */
package ro.isdc.wro.extensions.processor.algorithm.csslint;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import org.mozilla.javascript.RhinoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.extensions.script.RhinoScriptBuilder;
import ro.isdc.wro.extensions.script.RhinoUtils;
import ro.isdc.wro.util.StopWatch;
import ro.isdc.wro.util.WroUtil;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


/**
 * CssLint script engine utility. The underlying implementation uses CSSLint script utility {@link https
 * ://github.com/stubbornella/csslint}.
 *
 * @author Alex Objelean
 * @since 1.3.8
 * @created 19 Jun 2011
 */
public class CssLint {
  private static final Logger LOG = LoggerFactory.getLogger(CssLint.class);
  private String[] options;

  /**
   * Initialize script builder for evaluation.
   */
  private RhinoScriptBuilder initScriptBuilder() {
    try {
      return RhinoScriptBuilder.newChain().evaluateChain(getStreamForCssLint(), "csslint.js");
    } catch (final IOException ex) {
      throw new IllegalStateException("Failed reading init script", ex);
    }
  }


  /**
   * @return the stream of the csslint script. Override this method to provide a different script version.
   */
  protected InputStream getStreamForCssLint() {
    return getClass().getResourceAsStream("csslint.js");
  }


  /**
   * Validates a js using jsHint and throws {@link CssLintException} if the js is invalid. If no exception is thrown, the
   * js is valid.
   *
   * @param data js content to process.
   * @throws CssLintException when parsed css has some kind of problems.
   */
  public void validate(final String data) throws CssLintException {
    try {
      final StopWatch watch = new StopWatch();
      watch.start("init");
      final RhinoScriptBuilder builder = initScriptBuilder();
      watch.stop();
      watch.start("cssLint");
      LOG.debug("options: " + Arrays.toString(this.options));
      final String script = buildCssLintScript(WroUtil.toJSMultiLineString(data), this.options);
      builder.evaluate(script, "CSSLint.verify").toString();
      LOG.debug("" + builder.addJSON().evaluate("JSON.stringify(result)", ""));
      final boolean valid = Boolean.parseBoolean(builder.evaluate("result.length == 0", "checkNoErrors").toString());
      if (!valid) {
        final String json = builder.addJSON().evaluate("JSON.stringify(result)", "CssLint messages").toString();
        LOG.debug("json {}", json);
        final Type type = new TypeToken<List<CssLintError>>() {}.getType();
        final List<CssLintError> errors = new Gson().fromJson(json, type);
        LOG.debug("errors {}", errors);
        throw new CssLintException().setErrors(errors);
      }
      LOG.debug("isValid: " + valid);
      watch.stop();
      LOG.debug(watch.prettyPrint());
    } catch (final RhinoException e) {
      throw new WroRuntimeException(RhinoUtils.createExceptionMessage(e), e);
    }
  }


  /**
   * @param data script to process.
   * @param options options to set as true
   * @return Script used to pack and return the packed result.
   */
  private String buildCssLintScript(final String data, final String... options) {
    final StringBuffer sb = new StringBuffer("{");
    if (options != null) {
      for (int i = 0; i < options.length; i++) {
        sb.append("\"" + options[i] + "\": 1");
        if (i < options.length - 1) {
          sb.append(",");
        }
      }
    }
    sb.append("}");
    /**
     * Handle the following <a href="https://github.com/stubbornella/csslint/issues/79">issue</a>.
     */
    final boolean noOptions = options == null || options.length == 0;
    final String optionsAsString = noOptions ? "" : "," + sb.toString();
    //return "var result = CSSLint.verify(" + data + "," + sb.toString() + ").messages;";
    return "var result = CSSLint.verify(" + data + optionsAsString + ").messages;";
  }


  /**
   * @param options the options to set
   */
  public CssLint setOptions(final String ... options) {
    LOG.debug("setOptions: {}", options);
    this.options = options == null ? new String[] {} : options;
    return this;
  }
}
