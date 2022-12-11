/*
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.persist.utils;

import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.persist.nodegenerator.BalSyntaxTreeGenerator;
import io.ballerina.persist.objects.BalException;
import io.ballerina.persist.objects.Entity;
import io.ballerina.persist.objects.EntityMetaData;
import io.ballerina.projects.DiagnosticResult;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.tools.diagnostics.Diagnostic;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import static io.ballerina.persist.nodegenerator.BalFileConstants.KEYWORD_ENTITY;
import static io.ballerina.persist.nodegenerator.BalFileConstants.PERSIST;
import static io.ballerina.persist.nodegenerator.BalSyntaxTreeGenerator.formatModuleMembers;
import static io.ballerina.persist.nodegenerator.BalSyntaxTreeGenerator.generateRelations;

/**
 * This Class implements the utility methods for persist tool.
 *
 * @since 0.1.0
 */

public class BalProjectUtils {

    private BalProjectUtils() {}

    public static EntityMetaData getEntities(Module module) throws BalException {
        ArrayList<Entity> entities = new ArrayList<>();
        ArrayList<ModuleMemberDeclarationNode> entityMemberNodes = new ArrayList<>();
        try {
            for (DocumentId documentId : module.documentIds()) {
                Document document = module.document(documentId);
                EntityMetaData retEntityMetaData = BalSyntaxTreeGenerator.getEntityMetadata(document.syntaxTree());
                ArrayList<Entity> entityMetadataList = retEntityMetaData.entityArray;
                ArrayList<ModuleMemberDeclarationNode> entityModuleMembersList =
                        retEntityMetaData.moduleMembersArray;
                for (Entity entityMetadata : entityMetadataList) {
                    entities.add(entityMetadata);
                    entityMemberNodes.add(entityModuleMembersList.get(entityMetadataList
                            .indexOf(entityMetadata)));
                }
            }
            generateRelations(entities);
            entityMemberNodes = formatModuleMembers(entityMemberNodes, entities);
            return new EntityMetaData(entities, entityMemberNodes);

        } catch (IOException e) {
            throw new BalException("Error while reading entities in the Ballerina project. " + e.getMessage());
        }
    }

    public static BuildProject getBuildProject(Path projectPath) throws BalException {
        BuildProject buildProject = BuildProject.load(projectPath.toAbsolutePath());
        Package currentPackage = buildProject.currentPackage();
        PackageCompilation compilation = currentPackage.getCompilation();
        DiagnosticResult diagnosticResult = compilation.diagnosticResult();
        if (diagnosticResult.hasErrors()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("error occurred when validating the project. ");
            int validErrors = 0;
            for (Diagnostic diagnostic : diagnosticResult.errors()) {
                if (!diagnostic.diagnosticInfo().code().equals(DiagnosticErrorCode
                        .INCOMPATIBLE_TYPES.diagnosticId())) {
                    errorMessage.append(System.lineSeparator());
                    errorMessage.append(diagnostic);
                    validErrors += 1;
                }
            }
            if (validErrors > 0) {
                throw new BalException(errorMessage.toString());
            }
        }
        return buildProject;
    }

    public static Module getEntityModule(BuildProject project) throws BalException {
        Module entityModule = null;
        Module defaultModule = null;
        for (Module module : project.currentPackage().modules()) {
            boolean entityExists = false;
            if (module.moduleName().moduleNamePart() == null) {
                defaultModule = module;
            }
            // TODO: remove this condition.
            if ("clients".equals(module.moduleName().moduleNamePart())) {
                continue;
            }
            for (DocumentId documentId : module.documentIds()) {
                Document document = module.document(documentId);
                ModulePartNode rootNote = document.syntaxTree().rootNode();
                NodeList<ModuleMemberDeclarationNode> nodeList = rootNote.members();
                for (ModuleMemberDeclarationNode moduleNode : nodeList) {
                    if (moduleNode.kind() != SyntaxKind.TYPE_DEFINITION || ((TypeDefinitionNode) moduleNode)
                            .metadata().isEmpty()) {
                        continue;
                    }

                    for (AnnotationNode annotation : ((TypeDefinitionNode) moduleNode).metadata().get().annotations()) {
                        Node annotReference = annotation.annotReference();
                        if (annotReference.kind() != SyntaxKind.QUALIFIED_NAME_REFERENCE) {
                            continue;
                        }
                        QualifiedNameReferenceNode qualifiedNameRef = (QualifiedNameReferenceNode) annotReference;
                        if (qualifiedNameRef.identifier().text().equals(KEYWORD_ENTITY) && qualifiedNameRef
                                .modulePrefix().text().equals(PERSIST) && annotation.annotValue()
                                .isPresent()) {
                            entityExists = true;
                        }
                    }
                }
            }
            if (entityExists) {
                if (entityModule == null) {
                    entityModule = module;
                } else {
                    throw new BalException("Entities are allowed to define in one module. " +
                            "but found in both " + entityModule.moduleName() + " and " +
                            module.moduleName());
                }
            }
        }
        return entityModule == null ? defaultModule : entityModule;
    }
}

