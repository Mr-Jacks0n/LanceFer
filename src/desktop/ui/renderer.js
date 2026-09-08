/**
 * LanceDrop Desktop — Logique Interactive du Renderer
 */

document.addEventListener('DOMContentLoaded', () => {
  console.log('LanceDrop Desktop Renderer Initialized');

  // ─────────────────────────────────────────────────────────────
  // 1. Gestion des Filtres de Catégories
  // ─────────────────────────────────────────────────────────────
  const filterPills = document.querySelectorAll('.filter-pill');
  const fileCards = document.querySelectorAll('.file-card');

  filterPills.forEach(pill => {
    pill.addEventListener('click', () => {
      filterPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');

      const filter = pill.getAttribute('data-filter');
      fileCards.forEach(card => {
        const cat = card.getAttribute('data-category');
        if (filter === 'all' || cat === filter) {
          card.style.display = 'flex';
        } else {
          card.style.display = 'none';
        }
      });
    });
  });

  // ─────────────────────────────────────────────────────────────
  // 2. Recherche en Temps Réel
  // ─────────────────────────────────────────────────────────────
  const searchInput = document.getElementById('searchInput');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      const query = e.target.value.toLowerCase().trim();
      fileCards.forEach(card => {
        const name = (card.getAttribute('data-name') || '').toLowerCase();
        if (!query || name.includes(query)) {
          card.style.display = 'flex';
        } else {
          card.style.display = 'none';
        }
      });
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 3. Sélection des Fichiers et Barre Inférieure
  // ─────────────────────────────────────────────────────────────
  const selectionBar = document.getElementById('selectionBar');
  const selectedCountText = document.getElementById('selectedCountText');
  const selectedSizeText = document.getElementById('selectedSizeText');

  function updateSelectionSummary() {
    const selectedCards = document.querySelectorAll('.file-card.selected');
    const count = selectedCards.length;

    if (count === 0) {
      selectionBar.style.display = 'none';
    } else {
      selectionBar.style.display = 'flex';
      selectedCountText.textContent = `${count} élément${count > 1 ? 's' : ''} sélectionné${count > 1 ? 's' : ''}`;
      // Calcul approximatif de taille
      selectedSizeText.textContent = `${(count * 4.2).toFixed(1)} Mo`;
    }
  }

  fileCards.forEach(card => {
    const checkbox = card.querySelector('.card-checkbox');

    // Clic sur la checkbox
    checkbox.addEventListener('click', (e) => {
      e.stopPropagation();
      card.classList.toggle('selected');
      checkbox.classList.toggle('checked');
      updateSelectionSummary();
    });

    // Clic sur la carte
    card.addEventListener('click', (e) => {
      // Ignorer si on clique sur le bouton menu
      if (e.target.closest('.card-menu-btn')) return;
      card.classList.toggle('selected');
      checkbox.classList.toggle('checked');
      updateSelectionSummary();
    });
  });

  // Initialisation de la sélection par défaut (Paysage_Alpes.jpg)
  updateSelectionSummary();

  // ─────────────────────────────────────────────────────────────
  // 4. Modale de Confirmation de Suppression
  // ─────────────────────────────────────────────────────────────
  const deleteModal = document.getElementById('deleteModal');
  const modalCloseBtn = document.getElementById('modalCloseBtn');
  const btnCancelDelete = document.getElementById('btnCancelDelete');
  const btnConfirmDelete = document.getElementById('btnConfirmDelete');
  const modalFileName = document.getElementById('modalFileName');
  const modalFileMeta = document.getElementById('modalFileMeta');
  let targetCardToDelete = null;

  function openDeleteModal(card) {
    targetCardToDelete = card;
    const name = card.getAttribute('data-name') || 'ce fichier';
    const meta = `${card.getAttribute('data-size') || ''} • ${card.getAttribute('data-date') || ''}`;
    
    modalFileName.textContent = name;
    modalFileMeta.textContent = meta;
    deleteModal.classList.add('open');
  }

  function closeDeleteModal() {
    deleteModal.classList.remove('open');
    targetCardToDelete = null;
  }

  modalCloseBtn.addEventListener('click', closeDeleteModal);
  btnCancelDelete.addEventListener('click', closeDeleteModal);
  deleteModal.addEventListener('click', (e) => {
    if (e.target === deleteModal) closeDeleteModal();
  });

  // Clic sur les boutons 3-dots pour ouvrir la modale
  document.querySelectorAll('.card-menu-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const card = btn.closest('.file-card');
      openDeleteModal(card);
    });
  });

  // Confirmation de suppression
  btnConfirmDelete.addEventListener('click', () => {
    if (targetCardToDelete) {
      targetCardToDelete.style.transform = 'scale(0.8)';
      targetCardToDelete.style.opacity = '0';
      setTimeout(() => {
        targetCardToDelete.remove();
        updateSelectionSummary();
        closeDeleteModal();
      }, 200);
    } else {
      // Suppression de tous les éléments sélectionnés
      const selected = document.querySelectorAll('.file-card.selected');
      selected.forEach(card => {
        card.style.opacity = '0';
        setTimeout(() => card.remove(), 200);
      });
      setTimeout(() => {
        updateSelectionSummary();
        closeDeleteModal();
      }, 220);
    }
  });

  // Bouton Supprimer de la barre inférieure
  const btnDeleteSelected = document.getElementById('btnDeleteSelected');
  if (btnDeleteSelected) {
    btnDeleteSelected.addEventListener('click', () => {
      const selected = document.querySelector('.file-card.selected');
      if (selected) {
        openDeleteModal(selected);
      }
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 5. Bouton "+ Ajouter des fichiers" & "Envoyer des fichiers"
  // ─────────────────────────────────────────────────────────────
  const btnAddFiles = document.getElementById('btnAddFiles');
  const btnSendFiles = document.getElementById('btnSendFiles');

  async function handleAddFiles() {
    if (window.lancedrop && window.lancedrop.openFileDialog) {
      try {
        const files = await window.lancedrop.openFileDialog();
        if (files && files.length > 0) {
          files.forEach(f => addNewFileCard(f.name));
        }
      } catch (err) {
        console.warn('Native dialog failed, using web input fallback', err);
        triggerWebFileInput();
      }
    } else {
      triggerWebFileInput();
    }
  }

  function triggerWebFileInput() {
    const input = document.createElement('input');
    input.type = 'file';
    input.multiple = true;
    input.onchange = (e) => {
      const files = Array.from(e.target.files);
      files.forEach(f => addNewFileCard(f.name, (f.size / (1024 * 1024)).toFixed(1) + ' Mo'));
    };
    input.click();
  }

  function addNewFileCard(name, size = '3,4 Mo') {
    const grid = document.getElementById('filesGrid');
    const newCard = document.createElement('div');
    newCard.className = 'file-card';
    newCard.setAttribute('data-category', 'docs');
    newCard.setAttribute('data-name', name);
    newCard.setAttribute('data-size', size);
    newCard.setAttribute('data-date', "Aujourd'hui");

    newCard.innerHTML = `
      <div class="card-checkbox">
        <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
      </div>
      <button class="card-menu-btn" title="Options"><svg viewBox="0 0 20 20" fill="currentColor"><path d="M10 6a2 2 0 110-4 2 2 0 010 4zM10 12a2 2 0 110-4 2 2 0 010 4zM10 18a2 2 0 110-4 2 2 0 010 4z"/></svg></button>
      <div class="card-thumb thumb-icon-doc icon-word">
        <div class="doc-badge badge-word">📁</div>
      </div>
      <div class="card-body">
        <div class="card-title" title="${name}">${name}</div>
        <div class="card-meta">${size} • Aujourd'hui</div>
        <div class="card-status status-success">
          <span class="status-dot-green"></span>
          <span>Prêt</span>
        </div>
      </div>
    `;

    // Attacher les événements
    const chk = newCard.querySelector('.card-checkbox');
    chk.addEventListener('click', (e) => {
      e.stopPropagation();
      newCard.classList.toggle('selected');
      chk.classList.toggle('checked');
      updateSelectionSummary();
    });
    newCard.addEventListener('click', () => {
      newCard.classList.toggle('selected');
      chk.classList.toggle('checked');
      updateSelectionSummary();
    });
    newCard.querySelector('.card-menu-btn').addEventListener('click', (e) => {
      e.stopPropagation();
      openDeleteModal(newCard);
    });

    grid.prepend(newCard);
  }

  if (btnAddFiles) btnAddFiles.addEventListener('click', handleAddFiles);
  if (btnSendFiles) btnSendFiles.addEventListener('click', handleAddFiles);

  // ─────────────────────────────────────────────────────────────
  // 6. Animation du Transfert Actif (Voyage_Côte.mp4)
  // ─────────────────────────────────────────────────────────────
  const transferPercent = document.getElementById('transferPercent');
  const transferProgressBar = document.getElementById('transferProgressBar');
  const transferSpeedText = document.getElementById('transferSpeedText');
  const btnPauseTransfer = document.getElementById('btnPauseTransfer');
  let currentPercent = 65;
  let isPaused = false;

  const transferInterval = setInterval(() => {
    if (isPaused) return;
    if (currentPercent < 100) {
      currentPercent += 1;
      const transferred = ((currentPercent / 100) * 28.7).toFixed(1);
      transferPercent.textContent = `${currentPercent}%`;
      transferProgressBar.style.width = `${currentPercent}%`;
      transferSpeedText.textContent = `12,4 Mo/s • ${transferred} Mo / 28,7 Mo`;
    } else {
      transferPercent.textContent = `100%`;
      transferSpeedText.textContent = `Terminé • 28,7 Mo`;
      clearInterval(transferInterval);
    }
  }, 900);

  if (btnPauseTransfer) {
    btnPauseTransfer.addEventListener('click', () => {
      isPaused = !isPaused;
      btnPauseTransfer.textContent = isPaused ? '▶' : '⏸';
      transferSpeedText.textContent = isPaused ? 'En pause' : '12,4 Mo/s • reprise';
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 7. Navigation de la Sidebar & Vues Principales
  // ─────────────────────────────────────────────────────────────
  const navItems = document.querySelectorAll('.sidebar-menu .nav-item');
  const libraryView = document.getElementById('libraryView');
  const clipboardView = document.getElementById('clipboardView');
  const navClipboardBadge = document.getElementById('navClipboardBadge');

  function switchView(viewName) {
    navItems.forEach(i => {
      if (i.getAttribute('data-view') === viewName) {
        i.classList.add('active');
      } else {
        i.classList.remove('active');
      }
    });

    if (viewName === 'clipboard') {
      if (libraryView) libraryView.style.display = 'none';
      if (clipboardView) clipboardView.style.display = 'block';
      if (navClipboardBadge) navClipboardBadge.style.display = 'none';
    } else {
      if (libraryView) libraryView.style.display = 'block';
      if (clipboardView) clipboardView.style.display = 'none';
    }
  }

  navItems.forEach(item => {
    item.addEventListener('click', () => {
      const view = item.getAttribute('data-view');
      switchView(view);
    });
  });

  // ─────────────────────────────────────────────────────────────
  // 8. Logique du Presse-papier Partagé (Clipboard Sync)
  // ─────────────────────────────────────────────────────────────
  const clipboardTextInput = document.getElementById('clipboardTextInput');
  const charCountLabel = document.getElementById('charCountLabel');
  const btnPasteFromLocal = document.getElementById('btnPasteFromLocal');
  const btnSendClipboard = document.getElementById('btnSendClipboard');
  const btnSyncClipboardNow = document.getElementById('btnSyncClipboardNow');
  const btnClearClipboardHistory = document.getElementById('btnClearClipboardHistory');
  const snippetsContainer = document.getElementById('snippetsContainer');
  const quickClipboardTextPreview = document.getElementById('quickClipboardTextPreview');
  const btnQuickCopyPill = document.getElementById('btnQuickCopyPill');
  const quickCopyPillText = document.getElementById('quickCopyPillText');
  const btnQuickOpenClipboard = document.getElementById('btnQuickOpenClipboard');

  // Compteur de caractères
  if (clipboardTextInput && charCountLabel) {
    clipboardTextInput.addEventListener('input', () => {
      const len = clipboardTextInput.value.length;
      charCountLabel.textContent = `${len} caractère${len > 1 ? 's' : ''}`;
    });
  }

  // Coller depuis le presse-papier PC
  async function pasteLocalClipboard() {
    try {
      let text = '';
      if (window.lancedrop && window.lancedrop.readClipboard) {
        text = await window.lancedrop.readClipboard();
      } else if (navigator.clipboard) {
        text = await navigator.clipboard.readText();
      }
      if (text && clipboardTextInput) {
        clipboardTextInput.value = text;
        const len = text.length;
        if (charCountLabel) charCountLabel.textContent = `${len} caractère${len > 1 ? 's' : ''}`;
      }
    } catch (err) {
      console.warn('Erreur lecture presse-papier:', err);
    }
  }

  if (btnPasteFromLocal) btnPasteFromLocal.addEventListener('click', pasteLocalClipboard);
  if (btnSyncClipboardNow) btnSyncClipboardNow.addEventListener('click', pasteLocalClipboard);

  // Envoyer le texte au smartphone
  if (btnSendClipboard && clipboardTextInput) {
    btnSendClipboard.addEventListener('click', async () => {
      const text = clipboardTextInput.value.trim();
      if (!text) {
        alert('Veuillez saisir ou coller un texte avant d\'envoyer.');
        return;
      }

      if (window.lancedrop && window.lancedrop.sendClipboardText) {
        await window.lancedrop.sendClipboardText(text);
      }

      // Ajout dans les récents
      addSnippetItem(text, 'Envoyé au Samsung Galaxy A54', 'À l\'instant');

      // Mettre à jour le widget latéral rapide
      if (quickClipboardTextPreview) quickClipboardTextPreview.textContent = text;

      // Feedback visuel sur le bouton
      const originalText = btnSendClipboard.innerHTML;
      btnSendClipboard.innerHTML = `<span>✔ Envoyé avec succès !</span>`;
      btnSendClipboard.style.backgroundColor = '#16A34A';
      setTimeout(() => {
        btnSendClipboard.innerHTML = originalText;
        btnSendClipboard.style.backgroundColor = '';
        clipboardTextInput.value = '';
        if (charCountLabel) charCountLabel.textContent = '0 caractère';
      }, 1600);
    });
  }

  // Copier un snippet
  async function copyTextToClipboard(text, btnElement, labelElement) {
    try {
      if (window.lancedrop && window.lancedrop.writeClipboard) {
        await window.lancedrop.writeClipboard(text);
      } else if (navigator.clipboard) {
        await navigator.clipboard.writeText(text);
      }

      if (btnElement) {
        const oldHtml = btnElement.innerHTML;
        btnElement.innerHTML = `<span>✔ Copié !</span>`;
        setTimeout(() => {
          btnElement.innerHTML = oldHtml;
        }, 1500);
      } else if (labelElement) {
        const old = labelElement.textContent;
        labelElement.textContent = '✔ Copié !';
        setTimeout(() => {
          labelElement.textContent = old;
        }, 1500);
      }
    } catch (err) {
      console.warn('Erreur écriture presse-papier:', err);
    }
  }

  // Attacher les boutons de copie existants
  function attachCopyHandlers() {
    document.querySelectorAll('.btn-copy-snippet').forEach(btn => {
      btn.onclick = () => {
        const text = btn.getAttribute('data-copy');
        copyTextToClipboard(text, btn);
      };
    });
  }
  attachCopyHandlers();

  // Bouton de copie rapide dans le volet droit
  if (btnQuickCopyPill && quickClipboardTextPreview) {
    btnQuickCopyPill.addEventListener('click', () => {
      const text = quickClipboardTextPreview.textContent.trim();
      copyTextToClipboard(text, null, quickCopyPillText);
    });
  }

  if (btnQuickOpenClipboard) {
    btnQuickOpenClipboard.addEventListener('click', () => {
      switchView('clipboard');
    });
  }

  // Effacer l'historique
  if (btnClearClipboardHistory && snippetsContainer) {
    btnClearClipboardHistory.addEventListener('click', () => {
      if (confirm('Voulez-vous effacer l\'historique du presse-papier partagé ?')) {
        snippetsContainer.innerHTML = `
          <div style="text-align: center; padding: 24px; color: #94A3B8; font-size: 0.85rem;">
            Aucun texte dans l'historique. Envoyez ou recevez un texte pour le voir ici.
          </div>
        `;
      }
    });
  }

  // Ajouter un nouvel item snippet
  function addSnippetItem(text, deviceName, timeStr) {
    if (!snippetsContainer) return;
    const item = document.createElement('div');
    item.className = 'snippet-item';
    item.innerHTML = `
      <div class="snippet-header">
        <div class="snippet-source">
          <span class="badge-device">${deviceName}</span>
          <span class="snippet-time">${timeStr}</span>
        </div>
        <button class="btn-copy-snippet">
          <svg viewBox="0 0 20 20" fill="currentColor"><path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z" /><path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 2H9a3 3 0 01-3-2z" /></svg>
          <span>Copier</span>
        </button>
      </div>
      <div class="snippet-content">${escapeHtml(text)}</div>
    `;

    const copyBtn = item.querySelector('.btn-copy-snippet');
    copyBtn.onclick = () => copyTextToClipboard(text, copyBtn);

    snippetsContainer.insertBefore(item, snippetsContainer.firstChild);
  }

  function escapeHtml(str) {
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
  }

  // Écoute des réceptions en temps réel depuis le smartphone
  if (window.lancedrop && window.lancedrop.onClipboardReceived) {
    window.lancedrop.onClipboardReceived((data) => {
      const content = data.text || data;
      addSnippetItem(content, data.senderDevice || 'Samsung Galaxy A54', 'À l\'instant');
      if (quickClipboardTextPreview) quickClipboardTextPreview.textContent = content;
      if (navClipboardBadge) navClipboardBadge.style.display = 'inline-block';
    });
  }
});

