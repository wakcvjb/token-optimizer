/*
 * QIMD Token Optimization Utility
 * Copyright (c) 2026 wakcvjb. All rights reserved.
 * This source code is licensed under the MIT license.
 */

package com.qimd.token;

import java.util.*;
import java.time.*;
import java.time.format.*;
import java.util.regex.*;

public class TokenOptimizer {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    public static class Message {
        public String role;
        public String content;
        public long timestamp;
        
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
        
        public Message(String role, String content, long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("role", role);
            map.put("content", content);
            map.put("timestamp", timestamp);
            return map;
        }
    }
    
    public static class OptimizationResult {
        public List<Message> optimizedMessages;
        public int originalTokenCount;
        public int optimizedTokenCount;
        public double savingsPercent;
        public String compressionMethod;
        
        public OptimizationResult(List<Message> optimized, int original, int optimizedCount, String method) {
            this.optimizedMessages = optimized;
            this.originalTokenCount = original;
            this.optimizedTokenCount = optimizedCount;
            this.savingsPercent = (1 - (double)optimizedCount / original) * 100;
            this.compressionMethod = method;
        }
    }
    
    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        String[] words = text.split("\\s+");
        int wordTokens = words.length;
        int punctuationTokens = text.replaceAll("[^\\p{Punct}]", "").length();
        int numberTokens = text.replaceAll("[^0-9]", "").length() / 3;
        return wordTokens + punctuationTokens + numberTokens;
    }
    
    public static List<Message> compressHistory(List<Message> history, int maxMessages) {
        if (history == null || history.size() <= maxMessages) {
            return history;
        }
        
        List<Message> compressed = new ArrayList<>();
        
        if (!history.isEmpty() && "system".equals(history.get(0).role)) {
            compressed.add(history.get(0));
        }
        
        int keepFromEnd = maxMessages - compressed.size();
        List<Message> toSummarize = history.subList(
            compressed.size(), 
            history.size() - keepFromEnd
        );
        
        if (!toSummarize.isEmpty()) {
            String summary = createSummary(toSummarize);
            compressed.add(new Message("assistant", "[SUMMARY] " + summary));
        }
        
        compressed.addAll(history.subList(history.size() - keepFromEnd, history.size()));
        return compressed;
    }
    
    private static String createSummary(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        Set<String> topics = new HashSet<>();
        int questionCount = 0;
        int answerCount = 0;
        
        for (Message msg : messages) {
            if ("user".equals(msg.role)) {
                questionCount++;
                String[] words = msg.content.toLowerCase().split("\\s+");
                for (String word : words) {
                    if (word.length() > 3 && !STOP_WORDS.contains(word)) {
                        topics.add(word);
                    }
                }
            } else if ("assistant".equals(msg.role)) {
                answerCount++;
            }
        }
        
        sb.append(questionCount).append("Q&A");
        if (!topics.isEmpty()) {
            sb.append(" about ");
            int i = 0;
            for (String topic : topics) {
                if (i++ > 5) break;
                sb.append(topic);
                if (i < Math.min(topics.size(), 5)) sb.append("/");
            }
        }
        return sb.toString();
    }
    
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the", "and", "for", "that", "this", "with", "from", "have", "are", "was",
        "what", "when", "where", "which", "how", "why", "can", "will", "would", "could"
    ));
    
    public static String compressConversation(List<Message> messages, int targetTokens) {
        if (messages == null || messages.isEmpty()) return "";
        
        int currentTokens = 0;
        StringBuilder result = new StringBuilder();
        
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            int msgTokens = estimateTokenCount(msg.content);
            
            if (currentTokens + msgTokens <= targetTokens) {
                result.insert(0, formatMessage(msg));
                currentTokens += msgTokens;
            } else {
                break;
            }
        }
        
        if (currentTokens == 0 && !messages.isEmpty()) {
            Message last = messages.get(messages.size() - 1);
            String truncated = truncateToTokenLimit(last.content, targetTokens);
            result.append(formatMessage(new Message(last.role, truncated, last.timestamp)));
        }
        
        return result.toString();
    }
    
    private static String formatMessage(Message msg) {
        return String.format("[%s] %s\n", msg.role.toUpperCase(), msg.content);
    }
    
    private static String truncateToTokenLimit(String text, int maxTokens) {
        String[] words = text.split("\\s+");
        int tokens = 0;
        StringBuilder truncated = new StringBuilder();
        
        for (String word : words) {
            int wordTokens = word.length() / 4 + 1;
            if (tokens + wordTokens <= maxTokens) {
                truncated.append(word).append(" ");
                tokens += wordTokens;
            } else {
                truncated.append("...");
                break;
            }
        }
        return truncated.toString().trim();
    }
    
    public static String toStructuredOutput(Map<String, Object> data) {
        StringBuilder json = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (count++ > 0) json.append(",");
            json.append("\"").append(entry.getKey().replaceAll("\\s+", "_")).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value.toString().replaceAll("\"", "\\\\\"")).append("\"");
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }
        }
        json.append("}");
        return json.toString();
    }
    
    public static String extractKeyInfo(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder result = new StringBuilder();
        
        for (String sentence : sentences) {
            if (result.length() + sentence.length() + 1 <= maxLength) {
                if (result.length() > 0) result.append(" ");
                result.append(sentence);
            } else {
                break;
            }
        }
        
        if (result.length() < text.length() && result.length() > 0) {
            result.append("...");
        }
        
        return result.toString();
    }
    
    public static OptimizationResult optimizeForBudget(List<Message> messages, int budgetTokens) {
        int originalTokens = 0;
        for (Message msg : messages) {
            originalTokens += estimateTokenCount(msg.content);
        }
        
        if (originalTokens <= budgetTokens) {
            return new OptimizationResult(messages, originalTokens, originalTokens, "none_needed");
        }
        
        List<Message> optimized = new ArrayList<>();
        int currentTokens = 0;
        int systemTokens = 0;
        
        for (Message msg : messages) {
            if ("system".equals(msg.role)) {
                String compressed = extractKeyInfo(msg.content, 200);
                int compressedTokens = estimateTokenCount(compressed);
                optimized.add(new Message(msg.role, compressed, msg.timestamp));
                systemTokens = compressedTokens;
                currentTokens += compressedTokens;
            }
        }
        
        int remainingBudget = budgetTokens - currentTokens;
        
        List<Message> nonSystem = new ArrayList<>();
        for (Message msg : messages) {
            if (!"system".equals(msg.role)) {
                nonSystem.add(msg);
            }
        }
        
        if (remainingBudget > 0) {
            for (int i = nonSystem.size() - 1; i >= 0 && remainingBudget > 0; i--) {
                Message msg = nonSystem.get(i);
                int maxMsgTokens = Math.min(remainingBudget, 500);
                String compressed = extractKeyInfo(msg.content, maxMsgTokens * 4);
                int msgTokens = estimateTokenCount(compressed);
                
                optimized.add(1 + optimized.size() - (optimized.size() - systemTokens), 
                    new Message(msg.role, compressed, msg.timestamp));
                remainingBudget -= msgTokens;
            }
        }
        
        if (optimized.size() == systemTokens && !nonSystem.isEmpty()) {
            Message last = nonSystem.get(nonSystem.size() - 1);
            String compressed = extractKeyInfo(last.content, budgetTokens - systemTokens);
            optimized.add(new Message(last.role, compressed, last.timestamp));
        }
        
        int optimizedTokens = 0;
        for (Message msg : optimized) {
            optimizedTokens += estimateTokenCount(msg.content);
        }
        
        return new OptimizationResult(optimized, originalTokens, optimizedTokens, "budget_constrained");
    }
    
    public static String deduplicateContext(List<String> contexts) {
        if (contexts == null || contexts.isEmpty()) return "";
        
        Set<String> unique = new LinkedHashSet<>();
        for (String ctx : contexts) {
            String normalized = ctx.toLowerCase().replaceAll("\\s+", " ").trim();
            if (normalized.length() > 10) {
                unique.add(ctx);
            }
        }
        
        return String.join("\n---\n", unique);
    }
    
    public static void main(String[] args) {
        List<Message> history = new ArrayList<>();
        history.add(new Message("system", "You are a helpful assistant specialized in Java programming."));
        history.add(new Message("user", "How do I read a file in Java?"));
        history.add(new Message("assistant", "You can use Files.readString(Path.of(\"file.txt\"))"));
        history.add(new Message("user", "What about reading a large file efficiently?"));
        history.add(new Message("assistant", "Use BufferedReader with Files.newBufferedReader()"));
        
        int originalTokens = 0;
        for (Message msg : history) {
            originalTokens += estimateTokenCount(msg.content);
        }
        
        System.out.println("Original tokens: " + originalTokens);
        
        List<Message> compressed = compressHistory(history, 3);
        int compressedTokens = 0;
        for (Message msg : compressed) {
            compressedTokens += estimateTokenCount(msg.content);
        }
        
        System.out.println("Compressed tokens: " + compressedTokens);
        System.out.printf("Saved: %.1f%%\n", (1 - (double)compressedTokens / originalTokens) * 100);
        
        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("tool name", "read file");
        toolCall.put("parameters", "path/to/file.txt");
        toolCall.put("encoding", "UTF-8");
        
        System.out.println("Structured output: " + toStructuredOutput(toolCall));
        
        OptimizationResult result = optimizeForBudget(history, 50);
        System.out.println("Optimization method: " + result.compressionMethod);
        System.out.printf("Final savings: %.1f%%\n", result.savingsPercent);
    }
}
