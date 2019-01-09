// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"

namespace document {

class BoolFieldValue : public FieldValue {
    bool _value;
    bool _altered;

public:
    BoolFieldValue(bool value=false);
    ~BoolFieldValue() override;

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    FieldValue *clone() const override;
    int compare(const FieldValue &rhs) const override;

    void printXml(XmlOutputStream &out) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;

    const DataType *getDataType() const override;
    bool hasChanged() const override;

    bool getValue() const { return _value; }
    void setValue(bool v) { _value = v; }

    FieldValue &assign(const FieldValue &rhs) override;

    DECLARE_IDENTIFIABLE(BoolFieldValue);
};

}