package vn.huce.dto;

/** Intent resolved by local rules or Gemini; classification alone never authorizes a write. */
public record ChatClassification(String intent, boolean modelUnavailable) {
}
