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
  // 7. Navigation de la Sidebar
  // ─────────────────────────────────────────────────────────────
  const navItems = document.querySelectorAll('.sidebar-menu .nav-item');
  navItems.forEach(item => {
    item.addEventListener('click', () => {
      navItems.forEach(i => i.classList.remove('active'));
      item.classList.add('active');
    });
  });
});
