package com.digdes.school;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaSchoolStarter {
    public JavaSchoolStarter() {
    }

    private final List<Map<String, Object>> table = new ArrayList<>();

    //auxiliaryMap - мапа, помогающая сохранить последовательность столбцов,
    // даже если их значения null
    private final Map<String, Object> auxiliaryMap = new LinkedHashMap<>();
    private Map<String, Object> paramsWithAnd = new LinkedHashMap<>();
    private Map<String, Object> paramsWithOr = new LinkedHashMap<>();

    private void resetAuxiliaryFields() {
        auxiliaryMap.put("id", null);
        auxiliaryMap.put("lastName", null);
        auxiliaryMap.put("age", null);
        auxiliaryMap.put("cost", null);
        auxiliaryMap.put("active", null);
        paramsWithAnd = null;
        paramsWithOr = null;
    }


    public List<Map<String, Object>> execute(String request) {
        resetAuxiliaryFields();
        String queryType = getRequestType(request);

        return switch (queryType) {
            case "insert" -> insert(request);
            case "update" -> update(request);
            case "delete" -> delete(request);
            case "select" -> select(request);
            default -> throw new QueryTypeException("\nInvalid request type: " + queryType.toUpperCase());
        };
    }


    private List<Map<String, Object>> insert(String request) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> parametersList = getParametersList(request);
        Map<String, Object> resultRow = getRow(parametersList);
        table.add(resultRow);
        result.add(getAffectedNonNullParameters(resultRow));

        return result;
    }

    private List<Map<String, Object>> update(String request) {
        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> updatesNonNull = new ArrayList<>();
        if (containsRegex("\\b(?i)where\\b", request)) {
            List<Map<String, Object>> meetingConditionsRows = getMeetingConditionsRowsFromTable(getRightFromWhereParams(request));
            Map<String, Object> newValues = getLeftFromWhereParams(request);
            updates = updateTableAndGetChanges(meetingConditionsRows, newValues);
        } else {
            List<String> parametersList = getParametersList(request);
            updates = updateTableAndGetChanges(table, getRow(parametersList));
        }
        for (var updatedRow : updates) {
            updatesNonNull.add(getAffectedNonNullParameters(updatedRow));
        }

        return updatesNonNull;
    }

    private List<Map<String, Object>> delete(String request) {
        List<Map<String, Object>> deletions;
        List<Map<String, Object>> deletionsNonNull = new ArrayList<>();
        if (containsRegex("\\b(?i)where\\b", request)) {
            deletions = getMeetingConditionsRowsFromTable(getRightFromWhereParams(request));
            table.removeAll(deletions);
        } else {
            deletions = new ArrayList<>(table);
            table.removeAll(deletions);
        }
        for (var deletedRow : deletions) {
            deletionsNonNull.add(getAffectedNonNullParameters(deletedRow));
        }
        return deletionsNonNull;
    }

    private List<Map<String, Object>> select(String request) {
        List<Map<String, Object>> selections = new ArrayList<>();
        List<Map<String, Object>> selectionsNonNull = new ArrayList<>();
        if (containsRegex("\\b(?i)where\\b", request)) {
            selections = getMeetingConditionsRowsFromTable(getRightFromWhereParams(request));
        } else {
            selections = new ArrayList<>(table);
        }
        for (var selectedRow : selections) {
            selectionsNonNull.add(getAffectedNonNullParameters(selectedRow));
        }

        return selectionsNonNull;
    }



    private Map<String, Object> getRow(List<String> parametersValues) {
        Map<String, Object> map = new LinkedHashMap<>(auxiliaryMap);
        int emptyParametersCounter = 0;
        for (String values : parametersValues) {
            String[] valueArr = values.split("=");
            String columnName = valueArr[0];

            try {
                checkParameterValidity(columnName, valueArr[1]);
                map.put(columnName, valueArr[1]);

            } catch (ArrayIndexOutOfBoundsException e) {
                emptyParametersCounter++;
            }
        }
        if (emptyParametersCounter == parametersValues.size()) {
            throw new ParameterException("\nAll parameters are empty: " + parametersValues + ". Put at least one\n");
        }
        return map;
    }

    private List<String> getParametersList(String request) {
        String garbage = request.substring(0, request.indexOf("'"));
        String parameters = request.replace(garbage, "");
        return Arrays.asList(parameters.replaceAll("[\\s'’\"]", "").split(","));
    }

    private String getRequestType(String request) {
        return request.toLowerCase().split(" ")[0];
    }

    private void checkParameterValidity(String key, Object value) {
        if (!key.equals("id") && !key.equals("lastName") && !key.equals("age") && !key.equals("cost") && !key.equals("active")) {
            throw new NoSuchColumnNameException("Invalid column name: " + key);
        }
        if (!value.equals("null")) {
            try {
                switch (key) {
                    case "id", "age" -> Long.parseLong(value.toString());
                    case "cost" -> Double.parseDouble(value.toString());
                    case "active" -> {
                        if (!value.equals("true") && !value.equals("false")) {
                            throw new ParameterException("\nValue " + value + " for " + key + " column should be true, false or null");
                        }
                    }
                }
            } catch (Exception e) {
                throw new ParameterException("\nValue " + value + " for " + key + " column is not permitted");
            }
        }
    }

    private Map<String, Object> getAffectedNonNullParameters(Map<String, Object> row) {
        Map<String, Object> copyRow = new LinkedHashMap<>(auxiliaryMap);
        copyRow.putAll(row);
        while (copyRow.values().remove(null));
        while (copyRow.values().remove("null"));
        return copyRow;
    }

    private String[] getParamsSplitByWhere(String request) {
        return request.split("\\b(?i)where\\b");
    }

    private Map<String, Object> getLeftFromWhereParams(String request) {
        return getRow(getParametersList(getParamsSplitByWhere(request)[0]));
    }

    private Map<String, Object> getRightFromWhereParams(String request) {
        return getParamsAfterWhereOperator(getParamsSplitByWhere(request)[1]);
    }

    private Map<String, Object> getParamsAfterWhereOperator(String rawConditionalParams) {
        if (containsRegex("\\b(?i)and\\b", rawConditionalParams)) {
            paramsWithAnd = getConditionalParameters(Arrays.asList(rawConditionalParams.replaceAll("[\\s'\"]", "").split("(?i)\\band\\b")));
            return paramsWithAnd;
        } else if (containsRegex("\\b(?i)or\\b", rawConditionalParams)) {
            paramsWithOr = getConditionalParameters(Arrays.asList(rawConditionalParams.replaceAll("[\\s'\"]", "").split("(?i)\\bor\\b")));
            return paramsWithOr;
        } else {
            return getConditionalParameters(getParametersList(rawConditionalParams));
        }
    }
    private boolean containsRegex(String regex, String str) {
        return Pattern.compile(regex).matcher(str).find();
    }

    private Map<String, Object> getConditionalParameters(List<String> paramsList) {
        Map<String, Object> paramsToBeFound = new LinkedHashMap<>();
        for (String parameter : paramsList) {
            String comparisonOperator = getComparisonOperator(parameter);
            String[] valueArr = parameter.split(comparisonOperator);
            String columnName = valueArr[0];

            try {
                checkParameterValidity(columnName, valueArr[1]);
                if (valueArr[1].equals("null")) {
                    throw new ParameterException("Null parameter for comparison is not permitted: " + valueArr[0] + comparisonOperator + valueArr[1]);
                }
                paramsToBeFound.put(columnName, comparisonOperator + valueArr[1]);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new ParameterException("Value for column " + columnName + " can not be empty after 'WHERE' operator.");
            }
        }
        return paramsToBeFound;
    }

    private String getComparisonOperator(String parameter) {
        String[] operators = new String[]{"!=", "%ilike%", "%ilike", "ilike%", "ilike", "%like%", "%like", "like%", "like", ">=", "<=", "<", ">", "="};
        return Arrays.stream(operators).filter(parameter::contains).findAny().orElseThrow(() -> new OperatorException("\nAcceptable operators: " + Arrays.toString(operators)));
    }

    private List<Map<String, Object>> getMeetingConditionsRowsFromTable(Map<String, Object> conditionalMap) {
        List<Map<String, Object>> result = new ArrayList<>();
        int successCounter = 0;

        nextTableRow:
        for (var tableRow : table) {
            for (String tableColumnName : tableRow.keySet()) {

                nextConditionalRow:
                for (String conditionalColumnName : conditionalMap.keySet()) {

                    if (tableColumnName.equals(conditionalColumnName)) {

                        boolean passes = passesTheConditions(tableRow.get(conditionalColumnName), conditionalMap.get(conditionalColumnName), conditionalColumnName);
                        if (paramsWithAnd == null && paramsWithOr != null) {
                            if (passes) {
                                result.add(tableRow);
                                continue nextTableRow;
                            }
                        } else if (paramsWithAnd != null && paramsWithOr == null) {
                            if (passes) {
                                if (++successCounter == conditionalMap.size()) {
                                    result.add(tableRow);
                                    successCounter = 0;
                                }
                                continue nextConditionalRow;
                            }
                        } else if (paramsWithAnd == null && paramsWithOr == null) {
                            if (passes) {
                                result.add(tableRow);
                                continue nextTableRow;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> updateTableAndGetChanges(List<Map<String, Object>> meetingConditionsRows, Map<String, Object> newValues) {
        List<Map<String, Object>> affectedRows = new ArrayList<>();

        for (var meetingConditionsRow : meetingConditionsRows) {
            for (var newPair : newValues.entrySet()) {

                if (newPair.getValue() != null) {
                    meetingConditionsRow.put(newPair.getKey(), newPair.getValue());
                    table.get(table.indexOf(meetingConditionsRow)).put(newPair.getKey(), newPair.getValue());
                }
            }
            affectedRows.add(meetingConditionsRow);
        }
        return affectedRows;
    }

    private boolean passesTheConditions(Object tableValue, Object conditionalValue, String columnName) {

        boolean result = false;

        String comparisonOperator = getComparisonOperator(conditionalValue.toString());
        Object cleanConditionalValue = conditionalValue.toString().replace(comparisonOperator, "");
        Object value1 = null;
        Object value2 = null;


        try {
            switch (columnName) {
                case "id", "age" -> {
                    value1 = Long.parseLong(tableValue.toString());
                    value2 = Long.parseLong(cleanConditionalValue.toString());
                }
                case "lastName" -> {
                    value1 = tableValue.toString();
                    value2 = cleanConditionalValue.toString();
                }
                case "cost" -> {
                    value1 = Double.parseDouble(tableValue.toString());
                    value2 = Double.parseDouble(cleanConditionalValue.toString());
                }
                case "active" -> {
                    value1 = Boolean.valueOf(tableValue.toString());
                    value2 = Boolean.valueOf(cleanConditionalValue.toString());
                }
            }

            switch (comparisonOperator) {
                case "!=" -> result = !value1.equals(value2);
                case "%ilike%" -> result = matchesLikePattern(value1.toString(), value2.toString(), "%ilike%");
                case "%ilike" -> result = matchesLikePattern(value1.toString(), value2.toString(), "%ilike");
                case "ilike%" -> result = matchesLikePattern(value1.toString(), value2.toString(), "ilike%");
                case "ilike" -> result = value1.toString().equalsIgnoreCase(value2.toString());
                case "%like%" -> result = matchesLikePattern(value1.toString(), value2.toString(), "%like%");
                case "%like" -> result = matchesLikePattern(value1.toString(), value2.toString(), "%like");
                case "like%" -> result = matchesLikePattern(value1.toString(), value2.toString(), "like%");
                case "like" -> result = value1.toString().equals(value2.toString());
                case ">=" -> {
                    if (value1 instanceof Double) {
                        result = (Double) value1 >= (Double) value2;
                    } else if (value1 instanceof Long) {
                        result = (Long) value1 >= (Long) value2;
                    }else{
                        throw new ParameterException(columnName + " is not compatible with " + comparisonOperator + value2);
                    }
                }
                case "<=" -> {
                    if (value1 instanceof Double) {
                        result = (Double) value1 <= (Double) value2;
                    } else if (value1 instanceof Long) {
                        result = (Long) value1 <= (Long) value2;
                    }else{
                        throw new ParameterException(columnName + " is not compatible with " + comparisonOperator + value2);
                    }
                }
                case ">" -> {
                    if (value1 instanceof Double) {
                        result = (Double) value1 > (Double) value2;
                    } else if (value1 instanceof Long) {
                        result = (Long) value1 > (Long) value2;
                    }else{
                        throw new ParameterException(columnName + " is not compatible with " + comparisonOperator + value2);
                    }
                }
                case "<" -> {
                    if (value1 instanceof Double) {
                        result = (Double) value1 < (Double) value2;
                    } else if (value1 instanceof Long) {
                        result = (Long) value1 < (Long) value2;
                    }else{
                        throw new ParameterException(columnName + " is not compatible with " + comparisonOperator + value2);
                    }
                }
                case "=" -> result = value1.equals(value2);
            }
        } catch (NullPointerException e) {
            result = comparisonOperator.equals("!=");
        }
        return result;
    }

    private boolean matchesLikePattern(String value1, String value2, String likeOperator) {

        String regex = switch (likeOperator) {
            case "%ilike%" -> "(?i).*" + value2 + ".*";
            case "%ilike" -> "(?i).*" + value2;
            case "ilike%" -> "(?i)" + value2 + ".*";
            case "ilike" -> "(?i)" + value2;
            case "%like%" -> ".*" + value2 + ".*";
            case "%like" -> ".*" + value2;
            case "like%" -> value2 + ".*";
            case "like" -> value2;
            default -> null;
        };

        Pattern pattern = Pattern.compile(regex, Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(value1);
        return matcher.matches();
    }
}
