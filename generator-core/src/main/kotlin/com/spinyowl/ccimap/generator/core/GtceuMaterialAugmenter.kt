package com.spinyowl.ccimap.generator.core

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

internal class GtceuMaterialAugmenter(
    private val resources: ZipResourceSet,
    private val colorResolver: BlockColorResolver
) {
    fun augment(colors: MutableMap<String, String>) {
        val materialDefinitions = loadMaterialDefinitions()
        var synthesizedBlocks = 0

        for (definition in materialDefinitions.values) {
            if (!definition.hasSolidForm) continue

            val blockId = "${definition.id}_block"
            if (colors.containsKey(blockId)) continue

            colors[blockId] = colorResolver.resolveGtceuMaterialBlockColor(definition)
            synthesizedBlocks++
        }

        if (synthesizedBlocks > 0) {
            System.err.println("Synthesized $synthesizedBlocks GTCEu material block colors.")
        }
    }

    private fun loadMaterialDefinitions(): Map<String, GtceuMaterialDefinition> {
        val (zip, entry) = resources.requireEntry(GTCEU_ELEMENT_MATERIALS_CLASS)
        val classNode = ClassNode()
        zip.getInputStream(entry).use { input ->
            ClassReader(input).accept(classNode, 0)
        }

        val registerMethod = classNode.methods.firstOrNull { it.name == "register" && it.desc == "()V" }
            ?: error("Unable to find GTCEu material register method")
        return parseMaterialDefinitions(registerMethod)
    }

    private fun parseMaterialDefinitions(method: MethodNode): Map<String, GtceuMaterialDefinition> {
        val stack = mutableListOf<BytecodeValue>()
        val locals = mutableMapOf<Int, BytecodeValue>()
        val materials = linkedMapOf<String, GtceuMaterialDefinition>()
        val iterator = method.instructions.iterator()

        while (iterator.hasNext()) {
            when (val instruction = iterator.next()) {
                is LdcInsnNode -> {
                    when (val constant = instruction.cst) {
                        is Int -> stack += IntValue(constant)
                        is Float -> stack += FloatValue(constant)
                        is String -> stack += StringValue(constant)
                        else -> stack += UnknownValue
                    }
                }

                is IntInsnNode -> stack += IntValue(instruction.operand)
                is InsnNode -> handleZeroOperandInstruction(instruction.opcode, stack)
                is VarInsnNode -> when (instruction.opcode) {
                    Opcodes.ALOAD -> stack += locals[instruction.`var`] ?: UnknownValue
                    Opcodes.ASTORE -> locals[instruction.`var`] = popOrUnknown(stack)
                }

                is TypeInsnNode -> if (instruction.opcode == Opcodes.NEW) stack += MarkerValue(instruction.desc)
                is FieldInsnNode -> if (instruction.opcode == Opcodes.GETSTATIC) stack += FieldValue(instruction.owner, instruction.name)
                is MethodInsnNode -> handleMethodInstruction(instruction, stack, materials)
            }
        }

        return materials
    }

    private fun handleZeroOperandInstruction(opcode: Int, stack: MutableList<BytecodeValue>) {
        when (opcode) {
            Opcodes.ACONST_NULL -> stack += UnknownValue
            Opcodes.ICONST_M1 -> stack += IntValue(-1)
            Opcodes.ICONST_0 -> stack += IntValue(0)
            Opcodes.ICONST_1 -> stack += IntValue(1)
            Opcodes.ICONST_2 -> stack += IntValue(2)
            Opcodes.ICONST_3 -> stack += IntValue(3)
            Opcodes.ICONST_4 -> stack += IntValue(4)
            Opcodes.ICONST_5 -> stack += IntValue(5)
            Opcodes.FCONST_0 -> stack += FloatValue(0f)
            Opcodes.FCONST_1 -> stack += FloatValue(1f)
            Opcodes.FCONST_2 -> stack += FloatValue(2f)
            Opcodes.DUP -> stack.lastOrNull()?.let { stack += it }
            Opcodes.POP -> popOrUnknown(stack)
        }
    }

    private fun handleMethodInstruction(
        instruction: MethodInsnNode,
        stack: MutableList<BytecodeValue>,
        materials: MutableMap<String, GtceuMaterialDefinition>
    ) {
        val argumentCount = countMethodArguments(instruction.desc)
        val arguments = MutableList(argumentCount) { popOrUnknown(stack) }.asReversed()
        val receiver = if (instruction.opcode != Opcodes.INVOKESTATIC) popOrUnknown(stack) else null

        when {
            instruction.owner == GTCEU_OWNER && instruction.name == "id" -> {
                val id = (arguments.firstOrNull() as? StringValue)?.value
                if (id == null) {
                    stack += UnknownValue
                    return
                }
                stack += ResourceLocationValue(id)
            }

            instruction.owner == MATERIAL_BUILDER_OWNER && instruction.name == "<init>" -> {
                val id = (arguments.firstOrNull() as? ResourceLocationValue)?.id
                if (receiver is MarkerValue && id != null) {
                    val definition = materials.getOrPut(id) { GtceuMaterialDefinition(id) }
                    stack += BuilderValue(definition)
                } else {
                    stack += UnknownValue
                }
            }

            instruction.owner == MATERIAL_BUILDER_OWNER -> {
                val builder = receiver as? BuilderValue
                if (builder == null) {
                    if (returnsValue(instruction.desc)) stack += UnknownValue
                    return
                }

                when (instruction.name) {
                    "ingot", "gem" -> builder.definition.hasSolidForm = true
                    "color" -> builder.definition.primaryColor =
                        (arguments.firstOrNull() as? IntValue)?.value ?: builder.definition.primaryColor
                    "secondaryColor" -> builder.definition.secondaryColor =
                        (arguments.firstOrNull() as? IntValue)?.value ?: builder.definition.secondaryColor
                    "iconSet" -> {
                        val iconSet = (arguments.firstOrNull() as? FieldValue)
                            ?.takeIf { it.owner == MATERIAL_ICON_SET_OWNER }
                            ?.name
                            ?.lowercase()
                        if (iconSet != null) builder.definition.iconSet = iconSet
                    }
                    "buildAndRegister" -> {
                        materials[builder.definition.id] = builder.definition
                        stack += UnknownValue
                        return
                    }
                }

                if (returnsValue(instruction.desc)) stack += builder
            }

            else -> if (returnsValue(instruction.desc)) stack += UnknownValue
        }
    }

    private fun popOrUnknown(stack: MutableList<BytecodeValue>): BytecodeValue =
        if (stack.isEmpty()) UnknownValue else stack.removeLast()

    private fun countMethodArguments(descriptor: String): Int {
        var count = 0
        var index = descriptor.indexOf('(') + 1
        while (descriptor[index] != ')') {
            when (descriptor[index]) {
                'L' -> {
                    index = descriptor.indexOf(';', index) + 1
                    count++
                }
                '[' -> {
                    while (descriptor[index] == '[') index++
                    index = if (descriptor[index] == 'L') descriptor.indexOf(';', index) + 1 else index + 1
                    count++
                }
                else -> {
                    index++
                    count++
                }
            }
        }
        return count
    }

    private fun returnsValue(descriptor: String): Boolean = !descriptor.endsWith(")V")

    private sealed interface BytecodeValue
    private data class IntValue(val value: Int) : BytecodeValue
    private data class FloatValue(val value: Float) : BytecodeValue
    private data class StringValue(val value: String) : BytecodeValue
    private data class ResourceLocationValue(val id: String) : BytecodeValue
    private data class BuilderValue(val definition: GtceuMaterialDefinition) : BytecodeValue
    private data class MarkerValue(val owner: String) : BytecodeValue
    private data class FieldValue(val owner: String, val name: String) : BytecodeValue
    private data object UnknownValue : BytecodeValue

    private companion object {
        const val GTCEU_ELEMENT_MATERIALS_CLASS = "com/gregtechceu/gtceu/common/data/materials/ElementMaterials.class"
        const val GTCEU_OWNER = "com/gregtechceu/gtceu/GTCEu"
        const val MATERIAL_BUILDER_OWNER = "com/gregtechceu/gtceu/api/data/chemical/material/Material\$Builder"
        const val MATERIAL_ICON_SET_OWNER = "com/gregtechceu/gtceu/api/data/chemical/material/info/MaterialIconSet"
    }
}
