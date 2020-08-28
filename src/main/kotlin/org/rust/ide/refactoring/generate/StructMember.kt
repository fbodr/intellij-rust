/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate

import org.rust.lang.core.psi.RsNamedFieldDecl
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.ext.isTupleStruct
import org.rust.lang.core.psi.ext.namedFields
import org.rust.lang.core.psi.ext.positionalFields
import org.rust.lang.core.psi.substAndGetText
import org.rust.lang.core.types.Substitution

data class StructMember(
    val argumentIdentifier: String,
    val fieldIdentifier: String,
    val typeReferenceText: String
) {
    val dialogRepresentation: String get() = "$argumentIdentifier: $typeReferenceText"

    companion object {
        fun fromStruct(structItem: RsStructItem, substitution: Substitution): List<StructMember> {
            return if (structItem.isTupleStruct) {
                fromTupleList(structItem.positionalFields, substitution)
            } else {
                fromFieldList(structItem.namedFields, substitution)
            }
        }

        private fun fromTupleList(tupleFieldList: List<RsTupleFieldDecl>, substitution: Substitution): List<StructMember> {
            return tupleFieldList.mapIndexed { index, tupleField ->
                val typeName = tupleField.typeReference.substAndGetText(substitution)
                StructMember("field$index", "()", typeName)
            }
        }

        private fun fromFieldList(fieldDeclList: List<RsNamedFieldDecl>, substitution: Substitution): List<StructMember> {
            return fieldDeclList.map {
                StructMember(
                    it.identifier.text ?: "()",
                    it.identifier.text + ":()",
                    it.typeReference?.substAndGetText(substitution) ?: "()"
                )
            }
        }
    }
}
