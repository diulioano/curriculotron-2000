package dev.curriculotron.shared.web;

import java.util.List;

public record ApiListResponse<T>(List<T> items, String dataAsOf) {
  public ApiListResponse {
    items = List.copyOf(items);
  }
}
