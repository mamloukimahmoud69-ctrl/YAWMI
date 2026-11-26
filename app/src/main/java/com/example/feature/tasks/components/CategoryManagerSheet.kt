package com.example.feature.tasks.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.CategoryEntity
import com.example.domain.category.CategoryDomainResult
import com.example.domain.category.CategoryInput
import com.example.domain.category.CategoryUseCases
import com.example.ui.theme.LocalYawmiSemanticColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerSheet(
  categories: List<CategoryEntity>,
  categoryUseCases: CategoryUseCases,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val semantic = LocalYawmiSemanticColors.current

  var editingCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
  var nameInput by rememberSaveable { mutableStateOf("") }
  var selectedColorToken by rememberSaveable { mutableStateOf(CategoryTokens.COLOR_TOKENS.first()) }
  var selectedIconName by rememberSaveable { mutableStateOf(CategoryTokens.ICON_TOKENS.first()) }
  var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = modifier.testTag("category_manager_sheet"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.category_manager_title),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("category_close_btn"),
        ) {
          Text(text = stringResource(R.string.action_close))
        }
      }

      OutlinedTextField(
        value = nameInput,
        onValueChange = {
          nameInput = it
          errorMessage = null
        },
        label = { Text(stringResource(R.string.category_name_label)) },
        placeholder = { Text(stringResource(R.string.category_name_hint)) },
        singleLine = true,
        isError = errorMessage != null,
        supportingText =
          errorMessage?.let { msg -> { Text(msg, modifier = Modifier.testTag("category_name_error")) } },
        modifier = Modifier.fillMaxWidth().testTag("category_name_input"),
      )

      Text(
        text = stringResource(R.string.category_color_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CategoryTokens.COLOR_TOKENS.forEach { token ->
          val swatchColor = CategoryTokens.resolveColor(token)
          val selected = selectedColorToken == token
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .clickable { selectedColorToken = token }
                .testTag("category_color_$token"),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(32.dp)
                  .clip(CircleShape)
                  .background(swatchColor)
                  .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color =
                      if (selected) MaterialTheme.colorScheme.onSurface
                      else semantic.borderSubtle,
                    shape = CircleShape,
                  ),
            ) {
              if (selected) {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = token,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (editingCategoryId != null) {
          OutlinedButton(
            onClick = {
              editingCategoryId = null
              nameInput = ""
              errorMessage = null
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("category_cancel_edit_btn"),
          ) {
            Text(stringResource(R.string.action_cancel))
          }
        }
        Button(
          enabled = !isSaving,
          onClick = {
            isSaving = true
            scope.launch {
              val result =
                try {
                  val input =
                    CategoryInput(
                      name = nameInput,
                      colorToken = selectedColorToken,
                      iconName = selectedIconName,
                      sortOrder = categories.size,
                    )
                  val currentId = editingCategoryId
                  if (currentId == null) {
                    categoryUseCases.createCategory(input)
                  } else {
                    categoryUseCases.updateCategory(currentId, input)
                  }
                } finally {
                  isSaving = false
                }
              when (result) {
                is CategoryDomainResult.Success -> {
                  editingCategoryId = null
                  nameInput = ""
                  errorMessage = null
                }
                is CategoryDomainResult.Failure -> {
                  errorMessage = result.error.toLocalizedMessage(context)
                }
              }
            }
          },
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("category_save_btn"),
        ) {
          Text(
            text =
              if (editingCategoryId == null) stringResource(R.string.action_add)
              else stringResource(R.string.category_save_edit)
          )
        }
      }

      if (categories.isEmpty()) {
        Text(
          text = stringResource(R.string.category_empty_state),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 8.dp).testTag("category_empty_text"),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().height(220.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(categories, key = { it.id }) { cat ->
            val catColor = CategoryTokens.resolveColor(cat.colorToken)
            Card(
              colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              border = BorderStroke(1.dp, semantic.borderSubtle),
              shape = MaterialTheme.shapes.medium,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier = Modifier.size(14.dp).clip(CircleShape).background(catColor)
                )
                Icon(
                  imageVector = CategoryTokens.resolveIcon(cat.iconName),
                  contentDescription = null,
                  tint = catColor,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = cat.name,
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = {
                    editingCategoryId = cat.id
                    nameInput = cat.name
                    selectedColorToken = cat.colorToken
                    selectedIconName = cat.iconName
                    errorMessage = null
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("category_edit_${cat.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.action_edit),
                  )
                }
                IconButton(
                  onClick = {
                    scope.launch {
                      if (editingCategoryId == cat.id) {
                        editingCategoryId = null
                        nameInput = ""
                      }
                      categoryUseCases.deleteCategory(cat.id)
                    }
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("category_delete_${cat.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
