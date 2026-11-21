package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.OpportunityCorrectionService.DateChange;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Data de um payload parcial. A chave ausente deixa o componente do request em {@code null}
 * (mantém); {@code null} explícito ou texto vazio produz {@link #CLEAR}; uma data ISO produz o novo
 * valor. Qualquer outro token JSON, texto só com espaços ou formato diferente de {@code YYYY-MM-DD}
 * falha na desserialização (400).
 */
@JsonDeserialize(using = ClearableDate.Deserializer.class)
public record ClearableDate(LocalDate value) {
  public static final ClearableDate CLEAR = new ClearableDate(null);

  public static DateChange toChange(ClearableDate date) {
    if (date == null) return DateChange.UNCHANGED;
    return date.value == null ? DateChange.CLEAR : DateChange.set(date.value);
  }

  static final class Deserializer extends ValueDeserializer<ClearableDate> {
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    public ClearableDate deserialize(JsonParser parser, DeserializationContext context) {
      JsonToken token = parser.currentToken();
      if (token == JsonToken.VALUE_NULL) return CLEAR;
      if (token != JsonToken.VALUE_STRING) {
        return (ClearableDate) context.handleUnexpectedToken(ClearableDate.class, parser);
      }
      String text = parser.getValueAsString();
      if (text.isEmpty()) return CLEAR;
      if (!ISO_DATE.matcher(text).matches()) throw invalid(context, text);
      try {
        return new ClearableDate(LocalDate.parse(text));
      } catch (DateTimeParseException e) {
        throw invalid(context, text);
      }
    }

    private static DatabindException invalid(DeserializationContext context, String text) {
      return context.weirdStringException(text, ClearableDate.class, "esperado YYYY-MM-DD");
    }

    @Override
    public Object getAbsentValue(DeserializationContext context) {
      return null;
    }

    @Override
    public ClearableDate getNullValue(DeserializationContext context) {
      return CLEAR;
    }
  }
}
