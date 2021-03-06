// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/test/test_utils.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>

using vespalib::eval::Value;
using vespalib::eval::TensorSpec;
using vespalib::tensor::test::makeTensor;
using namespace vespalib::tensor;

namespace {

double
replace(double, double b)
{
    return b;
}

}

void
checkUpdate(const TensorSpec &source, const TensorSpec &update, const TensorSpec &expect)
{
    auto sourceTensor = makeTensor<Tensor>(source);
    auto updateTensor = makeTensor<SparseTensor>(update);
    const CellValues cellValues(*updateTensor);

    auto actualTensor = sourceTensor->modify(replace, cellValues);
    auto actual = actualTensor->toSpec();
    auto expectTensor = makeTensor<Tensor>(expect);
    auto expectPadded = expectTensor->toSpec();
    EXPECT_EQUAL(actual, expectPadded);
}

TEST("require that sparse tensors can be modified") {
    checkUpdate(TensorSpec("tensor(x{},y{})")
                .add({{"x","8"},{"y","9"}}, 11)
                .add({{"x","9"},{"y","9"}}, 11),
                TensorSpec("tensor(x{},y{})")
                .add({{"x","8"},{"y","9"}}, 2),
                TensorSpec("tensor(x{},y{})")
                .add({{"x","8"},{"y","9"}}, 2)
                .add({{"x","9"},{"y","9"}}, 11));
}

TEST("require that dense tensors can be modified") {
    checkUpdate(TensorSpec("tensor(x[10],y[10])")
                .add({{"x",8},{"y",9}}, 11)
                .add({{"x",9},{"y",9}}, 11),
                TensorSpec("tensor(x{},y{})")
                .add({{"x","8"},{"y","9"}}, 2),
                TensorSpec("tensor(x[10],y[10])")
                .add({{"x",8},{"y",9}}, 2)
                .add({{"x",9},{"y",9}}, 11));
}

TEST("require that sparse tensors ignore updates to missing cells") {
    checkUpdate(TensorSpec("tensor(x{},y{})")
                .add({{"x","8"},{"y","9"}}, 11)
                .add({{"x","9"},{"y","9"}}, 11),
                TensorSpec("tensor(x{},y{})")
                .add({{"x","7"},{"y","9"}}, 2)
                .add({{"x","8"},{"y","9"}}, 2),
                TensorSpec("tensor(x{},y{})")
                .add({{"x","8"},{"y","9"}}, 2)
                .add({{"x","9"},{"y","9"}}, 11));
}

TEST("require that dense tensors ignore updates to out of range cells") {
    checkUpdate(TensorSpec("tensor(x[10],y[10])")
                .add({{"x",8},{"y",9}}, 11)
                .add({{"x",9},{"y",9}}, 11),
                TensorSpec("tensor(x{},y{})")
                .add({{"x","8"},{"y","9"}}, 2)
                .add({{"x","10"},{"y","9"}}, 2),
                TensorSpec("tensor(x[10],y[10])")
                .add({{"x",8},{"y",9}}, 2)
                .add({{"x",9},{"y",9}}, 11));
}

TEST_MAIN() { TEST_RUN_ALL(); }
