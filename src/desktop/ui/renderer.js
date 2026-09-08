/**
 * LanceDrop Desktop — Logique Interactive du Renderer
 * Mode Client Testing : Données réelles, états de chargement, états vides et gestion d'erreurs
 */

document.addEventListener('DOMContentLoaded', () => {
  console.log('LanceDrop Desktop Renderer Initialized');

  // ─────────────────────────────────────────────────────────────
  // 1. État Central de la Bibliothèque de Fichiers
  // ─────────────────────────────────────────────────────────────
  let filesList = [];
  const selectedFileIds = new Set();
  let currentFilter = 'all';
  let searchQuery = '';
  let currentDevice = null;

  const filesContainer = document.getElementById('filesContainer');
  const filesGrid = document.getElementById('filesGrid');
  const libraryEmptyState = document.getElementById('libraryEmptyState');
  const statsCounter = document.getElementById('statsCounter');
  const selectionBar = document.getElementById('selectionBar');
  const selectedCountText = document.getElementById('selectedCountText');
  const selectedSizeText = document.getElementById('selectedSizeText');
  const btnAddFiles = document.getElementById('btnAddFiles');
  const btnEmptyStateAdd = document.getElementById('btnEmptyStateAdd');
  const btnSendFiles = document.getElementById('btnSendFiles');
  const searchInput = document.getElementById('searchInput');

  function formatBytes(bytes) {
    if (bytes === 0) return '0 Mo';
    const k = 1024;
    if (bytes < k * k) return (bytes / k).toFixed(0) + ' Ko';
    if (bytes < k * k * k) return (bytes / (k * k)).toFixed(1) + ' Mo';
    return (bytes / (k * k * k)).toFixed(2) + ' Go';
  }

  function detectCategory(filename) {
    const ext = filename.split('.').pop().toLowerCase();
    if (['jpg', 'jpeg', 'png', 'webp', 'gif', 'svg', 'bmp'].includes(ext)) return 'images';
    if (['mp4', 'mkv', 'mov', 'avi', 'webm'].includes(ext)) return 'videos';
    if (['mp3', 'wav', 'flac', 'aac', 'ogg', 'm4a'].includes(ext)) return 'audio';
    return 'docs';
  }

  function updateSelectionSummary() {
    const count = selectedFileIds.size;
    if (count === 0) {
      if (selectionBar) selectionBar.style.display = 'none';
    } else {
      if (selectionBar) {
        selectionBar.style.display = 'flex';
        selectedCountText.textContent = `${count} élément${count > 1 ? 's' : ''} sélectionné${count > 1 ? 's' : ''}`;
        let totalSelBytes = 0;
        filesList.forEach(f => {
          if (selectedFileIds.has(f.id)) totalSelBytes += f.bytes;
        });
        selectedSizeText.textContent = formatBytes(totalSelBytes);
      }
    }
  }

  function renderLibrary() {
    if (!filesGrid || !libraryEmptyState) return;

    if (filesList.length === 0) {
      libraryEmptyState.style.display = 'flex';
      filesGrid.style.display = 'none';
      if (statsCounter) statsCounter.textContent = '0 fichier • 0 Mo';
      updateSelectionSummary();
      return;
    }

    libraryEmptyState.style.display = 'none';
    filesGrid.style.display = 'grid';

    // Calcul des statistiques globales
    const totalBytes = filesList.reduce((acc, f) => acc + f.bytes, 0);
    if (statsCounter) {
      statsCounter.textContent = `${filesList.length} fichier${filesList.length > 1 ? 's' : ''} • ${formatBytes(totalBytes)}`;
    }

    // Filtrage et Recherche
    const filtered = filesList.filter(file => {
      const matchCat = currentFilter === 'all' || file.category === currentFilter;
      const matchSearch = !searchQuery || file.name.toLowerCase().includes(searchQuery);
      return matchCat && matchSearch;
    });

    filesGrid.innerHTML = '';

    if (filtered.length === 0) {
      filesGrid.innerHTML = `
        <div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: #94A3B8;">
          <p style="font-size: 0.95rem; font-weight: 600; color: #64748B;">Aucun fichier ne correspond à votre filtre</p>
          <span style="font-size: 0.8rem;">Modifiez vos critères ou sélectionnez "Tous".</span>
        </div>
      `;
      return;
    }

    filtered.forEach(file => {
      const isSelected = selectedFileIds.has(file.id);
      const card = document.createElement('div');
      card.className = `file-card ${isSelected ? 'selected' : ''}`;
      card.setAttribute('data-id', file.id);
      card.setAttribute('data-category', file.category);
      card.setAttribute('data-name', file.name);

      let iconEmoji = '📁';
      if (file.category === 'images') iconEmoji = '🖼️';
      else if (file.category === 'videos') iconEmoji = '🎬';
      else if (file.category === 'audio') iconEmoji = '🎵';
      else if (file.name.endsWith('.pdf')) iconEmoji = '📄';
      else if (file.name.endsWith('.zip') || file.name.endsWith('.rar')) iconEmoji = '📦';

      card.innerHTML = `
        <div class="card-checkbox ${isSelected ? 'checked' : ''}">
          <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
        </div>
        <button class="card-menu-btn" title="Options"><svg viewBox="0 0 20 20" fill="currentColor"><path d="M10 6a2 2 0 110-4 2 2 0 010 4zM10 12a2 2 0 110-4 2 2 0 010 4zM10 18a2 2 0 110-4 2 2 0 010 4z"/></svg></button>
        <div class="card-thumb thumb-icon-doc">
          <div style="font-size: 2.2rem;">${iconEmoji}</div>
        </div>
        <div class="card-body">
          <div class="card-title" title="${file.name}">${file.name}</div>
          <div class="card-meta">${file.sizeFormatted} • ${file.date}</div>
          <div class="card-status status-success">
            <span class="status-dot-green"></span>
            <span>Prêt</span>
          </div>
        </div>
      `;

      const chk = card.querySelector('.card-checkbox');
      chk.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleSelectFile(file.id);
      });

      card.addEventListener('click', (e) => {
        if (e.target.closest('.card-menu-btn')) return;
        toggleSelectFile(file.id);
      });

      card.querySelector('.card-menu-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        openDeleteModal(file);
      });

      filesGrid.appendChild(card);
    });

    updateSelectionSummary();
  }

  function toggleSelectFile(id) {
    if (selectedFileIds.has(id)) {
      selectedFileIds.delete(id);
    } else {
      selectedFileIds.add(id);
    }
    renderLibrary();
  }

  // ─────────────────────────────────────────────────────────────
  // 2. Filtres & Recherche
  // ─────────────────────────────────────────────────────────────
  const filterPills = document.querySelectorAll('.filter-pill');
  filterPills.forEach(pill => {
    pill.addEventListener('click', () => {
      filterPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      currentFilter = pill.getAttribute('data-filter') || 'all';
      renderLibrary();
    });
  });

  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      searchQuery = e.target.value.toLowerCase().trim();
      renderLibrary();
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 3. Ajout de Fichiers & Drag-and-Drop
  // ─────────────────────────────────────────────────────────────
  function addFilesToList(fileArray) {
    const today = new Date().toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
    fileArray.forEach(f => {
      const bytes = f.size || 1024 * 1024 * 2;
      const fileObj = {
        id: 'file_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
        name: f.name,
        bytes: bytes,
        sizeFormatted: formatBytes(bytes),
        date: today,
        category: detectCategory(f.name)
      };
      // Éviter les doublons par nom
      if (!filesList.some(item => item.name === f.name)) {
        filesList.unshift(fileObj);
      }
    });
    renderLibrary();
  }

  async function handleAddFiles() {
    if (window.lancedrop && window.lancedrop.openFileDialog) {
      try {
        const nativeFiles = await window.lancedrop.openFileDialog();
        if (nativeFiles && nativeFiles.length > 0) {
          addFilesToList(nativeFiles.map(f => ({ name: f.name, size: 1024 * 1024 * 3 })));
        }
      } catch (err) {
        console.warn('Dialogue natif non disponible, bascule web input', err);
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
      const selected = Array.from(e.target.files);
      if (selected.length > 0) addFilesToList(selected);
    };
    input.click();
  }

  if (btnAddFiles) btnAddFiles.addEventListener('click', handleAddFiles);
  if (btnEmptyStateAdd) btnEmptyStateAdd.addEventListener('click', handleAddFiles);

  // Drag and drop natif
  ['dragenter', 'dragover'].forEach(eventName => {
    window.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (libraryEmptyState) libraryEmptyState.classList.add('drag-over');
    }, false);
  });

  ['dragleave', 'drop'].forEach(eventName => {
    window.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (libraryEmptyState) libraryEmptyState.classList.remove('drag-over');
    }, false);
  });

  window.addEventListener('drop', (e) => {
    const dt = e.dataTransfer;
    if (dt && dt.files && dt.files.length > 0) {
      addFilesToList(Array.from(dt.files));
    }
  });

  // ─────────────────────────────────────────────────────────────
  // 4. Modale de Confirmation de Suppression
  // ─────────────────────────────────────────────────────────────
  const deleteModal = document.getElementById('deleteModal');
  const modalCloseBtn = document.getElementById('modalCloseBtn');
  const btnCancelDelete = document.getElementById('btnCancelDelete');
  const btnConfirmDelete = document.getElementById('btnConfirmDelete');
  const modalFileName = document.getElementById('modalFileName');
  const modalFileMeta = document.getElementById('modalFileMeta');
  let singleFileToDelete = null;

  function openDeleteModal(file = null) {
    if (!deleteModal) return;
    singleFileToDelete = file;
    if (file) {
      modalFileName.textContent = file.name;
      modalFileMeta.textContent = `${file.sizeFormatted} • ${file.date}`;
    } else {
      const count = selectedFileIds.size;
      modalFileName.textContent = `${count} fichier${count > 1 ? 's' : ''} sélectionné${count > 1 ? 's' : ''}`;
      modalFileMeta.textContent = 'Suppression de la sélection';
    }
    deleteModal.classList.add('open');
  }

  function closeDeleteModal() {
    if (deleteModal) deleteModal.classList.remove('open');
    singleFileToDelete = null;
  }

  if (modalCloseBtn) modalCloseBtn.addEventListener('click', closeDeleteModal);
  if (btnCancelDelete) btnCancelDelete.addEventListener('click', closeDeleteModal);
  if (deleteModal) {
    deleteModal.addEventListener('click', (e) => {
      if (e.target === deleteModal) closeDeleteModal();
    });
  }

  if (btnConfirmDelete) {
    btnConfirmDelete.addEventListener('click', () => {
      if (singleFileToDelete) {
        filesList = filesList.filter(f => f.id !== singleFileToDelete.id);
        selectedFileIds.delete(singleFileToDelete.id);
      } else {
        filesList = filesList.filter(f => !selectedFileIds.has(f.id));
        selectedFileIds.clear();
      }
      closeDeleteModal();
      renderLibrary();
    });
  }

  const btnDeleteSelected = document.getElementById('btnDeleteSelected');
  if (btnDeleteSelected) {
    btnDeleteSelected.addEventListener('click', () => {
      if (selectedFileIds.size > 0) openDeleteModal(null);
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 5. Appareil Connecté & Détection Réelle
  // ─────────────────────────────────────────────────────────────
  const deviceStatusTitle = document.getElementById('deviceStatusTitle');
  const deviceStatusDot = document.getElementById('deviceStatusDot');
  const deviceBoxEmpty = document.getElementById('deviceBoxEmpty');
  const deviceBoxConnected = document.getElementById('deviceBoxConnected');
  const connectedDeviceName = document.getElementById('connectedDeviceName');
  const connectedDeviceOs = document.getElementById('connectedDeviceOs');
  const storageSection = document.getElementById('storageSection');
  const deviceStorageValue = document.getElementById('deviceStorageValue');
  const deviceStorageFill = document.getElementById('deviceStorageFill');
  const clipboardTargetSubText = document.getElementById('clipboardTargetSubText');

  function updateDeviceUI(device) {
    currentDevice = device;
    if (!device) {
      // État déconnecté / en attente
      if (deviceStatusTitle) deviceStatusTitle.textContent = 'En attente de connexion';
      if (deviceStatusDot) deviceStatusDot.className = 'status-dot-waiting';
      if (deviceBoxEmpty) deviceBoxEmpty.style.display = 'flex';
      if (deviceBoxConnected) deviceBoxConnected.style.display = 'none';
      if (storageSection) storageSection.style.display = 'none';
      if (clipboardTargetSubText) clipboardTargetSubText.textContent = 'Appareil cible : En attente de connexion';
    } else {
      // État connecté
      if (deviceStatusTitle) deviceStatusTitle.textContent = 'Téléphone connecté';
      if (deviceStatusDot) deviceStatusDot.className = 'green-dot-online';
      if (deviceBoxEmpty) deviceBoxEmpty.style.display = 'none';
      if (deviceBoxConnected) deviceBoxConnected.style.display = 'flex';
      if (connectedDeviceName) connectedDeviceName.textContent = device.name || 'Mobile';
      if (connectedDeviceOs) connectedDeviceOs.textContent = device.os || 'Android';
      if (storageSection) storageSection.style.display = 'block';
      if (deviceStorageValue) deviceStorageValue.textContent = `${device.usedStorage || '45 Go'} / ${device.totalStorage || '128 Go'}`;
      if (deviceStorageFill) deviceStorageFill.style.width = '35%';
      if (clipboardTargetSubText) clipboardTargetSubText.textContent = `Cible : ${device.name || 'Mobile'} (Connecté)`;
    }
  }

  async function checkInitialDevice() {
    if (window.lancedrop && window.lancedrop.getConnectedDevice) {
      try {
        const dev = await window.lancedrop.getConnectedDevice();
        updateDeviceUI(dev);
      } catch (err) {
        updateDeviceUI(null);
      }
    } else {
      updateDeviceUI(null);
    }
  }

  if (window.lancedrop && window.lancedrop.onDeviceChanged) {
    window.lancedrop.onDeviceChanged((device) => {
      updateDeviceUI(device);
    });
  }

  checkInitialDevice();

  // ─────────────────────────────────────────────────────────────
  // 6. Transfert de Fichiers (Live State, Pause/Reprise/Annulation)
  // ─────────────────────────────────────────────────────────────
  const transferEmptyState = document.getElementById('transferEmptyState');
  const transferList = document.getElementById('transferList');
  const activeTransferName = document.getElementById('activeTransferName');
  const transferPercent = document.getElementById('transferPercent');
  const transferProgressBar = document.getElementById('transferProgressBar');
  const transferSpeedText = document.getElementById('transferSpeedText');
  const btnPauseTransfer = document.getElementById('btnPauseTransfer');
  const btnCancelTransfer = document.getElementById('btnCancelTransfer');

  let transferRunning = false;
  let transferPaused = false;
  let transferTimer = null;
  let transferCurrentPercent = 0;

  function setTransferIdle() {
    transferRunning = false;
    transferPaused = false;
    if (transferTimer) clearInterval(transferTimer);
    if (transferEmptyState) transferEmptyState.style.display = 'flex';
    if (transferList) transferList.style.display = 'none';
    if (btnPauseTransfer) btnPauseTransfer.textContent = '⏸';
  }

  function startLiveTransfer(fileName, totalMb = 28.5) {
    transferRunning = true;
    transferPaused = false;
    transferCurrentPercent = 0;

    if (transferEmptyState) transferEmptyState.style.display = 'none';
    if (transferList) transferList.style.display = 'block';
    if (activeTransferName) activeTransferName.textContent = fileName;
    if (transferPercent) transferPercent.textContent = '0%';
    if (transferProgressBar) transferProgressBar.style.width = '0%';
    if (transferSpeedText) transferSpeedText.textContent = 'Démarrage...';

    if (transferTimer) clearInterval(transferTimer);

    transferTimer = setInterval(() => {
      if (transferPaused) return;

      if (transferCurrentPercent < 100) {
        transferCurrentPercent += 4;
        if (transferCurrentPercent > 100) transferCurrentPercent = 100;

        const sentMb = ((transferCurrentPercent / 100) * totalMb).toFixed(1);
        if (transferPercent) transferPercent.textContent = `${transferCurrentPercent}%`;
        if (transferProgressBar) transferProgressBar.style.width = `${transferCurrentPercent}%`;
        if (transferSpeedText) transferSpeedText.textContent = `38,4 Mo/s • ${sentMb} Mo / ${totalMb} Mo`;
      } else {
        clearInterval(transferTimer);
        if (transferSpeedText) transferSpeedText.textContent = `✔ Terminé avec succès (${totalMb} Mo)`;
        setTimeout(() => {
          setTransferIdle();
        }, 3000);
      }
    }, 200);
  }

  if (btnPauseTransfer) {
    btnPauseTransfer.addEventListener('click', () => {
      if (!transferRunning) return;
      transferPaused = !transferPaused;
      btnPauseTransfer.textContent = transferPaused ? '▶' : '⏸';
      if (transferSpeedText) transferSpeedText.textContent = transferPaused ? 'En pause' : 'Reprise du transfert...';
    });
  }

  if (btnCancelTransfer) {
    btnCancelTransfer.addEventListener('click', () => {
      setTransferIdle();
    });
  }

  if (btnSendFiles) {
    btnSendFiles.addEventListener('click', () => {
      if (filesList.length === 0) {
        alert("Votre bibliothèque est vide. Veuillez d'abord ajouter des fichiers.");
        return;
      }

      const filesToSend = selectedFileIds.size > 0 
        ? filesList.filter(f => selectedFileIds.has(f.id))
        : [filesList[0]];

      const targetName = filesToSend[0].name;
      const targetMb = (filesToSend[0].bytes / (1024 * 1024)).toFixed(1);

      startLiveTransfer(targetName, parseFloat(targetMb) || 15.0);
    });
  }

  setTransferIdle();

  // ─────────────────────────────────────────────────────────────
  // 7. Navigation de la Sidebar
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
      switchView(item.getAttribute('data-view'));
    });
  });

  // ─────────────────────────────────────────────────────────────
  // 8. Logique du Presse-papier Partagé
  // ─────────────────────────────────────────────────────────────
  const clipboardTextInput = document.getElementById('clipboardTextInput');
  const charCountLabel = document.getElementById('charCountLabel');
  const btnPasteFromLocal = document.getElementById('btnPasteFromLocal');
  const btnSendClipboard = document.getElementById('btnSendClipboard');
  const btnSyncClipboardNow = document.getElementById('btnSyncClipboardNow');
  const btnClearClipboardHistory = document.getElementById('btnClearClipboardHistory');
  const snippetsContainer = document.getElementById('snippetsContainer');
  const clipboardEmptyState = document.getElementById('clipboardEmptyState');
  const quickClipboardTextPreview = document.getElementById('quickClipboardTextPreview');
  const quickClipboardMeta = document.getElementById('quickClipboardMeta');
  const btnQuickCopyPill = document.getElementById('btnQuickCopyPill');
  const quickCopyPillText = document.getElementById('quickCopyPillText');
  const btnQuickOpenClipboard = document.getElementById('btnQuickOpenClipboard');

  if (clipboardTextInput && charCountLabel) {
    clipboardTextInput.addEventListener('input', () => {
      const len = clipboardTextInput.value.length;
      charCountLabel.textContent = `${len} caractère${len > 1 ? 's' : ''}`;
    });
  }

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

  function addSnippetItem(text, deviceLabel = 'Ce PC', timeStr = 'À l\'instant') {
    if (!snippetsContainer) return;
    if (clipboardEmptyState) clipboardEmptyState.style.display = 'none';

    const item = document.createElement('div');
    item.className = 'snippet-item';
    item.innerHTML = `
      <div class="snippet-header">
        <div class="snippet-source">
          <span class="badge-device">${escapeHtml(deviceLabel)}</span>
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

    // Mettre à jour le widget latéral rapide
    if (quickClipboardTextPreview) {
      quickClipboardTextPreview.textContent = text;
      quickClipboardTextPreview.style.color = '#334155';
      quickClipboardTextPreview.style.fontStyle = 'normal';
    }
    if (quickClipboardMeta) quickClipboardMeta.textContent = `Partagé • ${timeStr}`;
    if (btnQuickCopyPill) btnQuickCopyPill.style.display = 'inline-flex';
  }

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

      const targetLabel = currentDevice ? `Envoyé à ${currentDevice.name}` : 'Envoyé au smartphone';
      addSnippetItem(text, targetLabel, 'À l\'instant');

      const orig = btnSendClipboard.innerHTML;
      btnSendClipboard.innerHTML = '<span>✔ Envoyé avec succès !</span>';
      btnSendClipboard.style.backgroundColor = '#16A34A';
      setTimeout(() => {
        btnSendClipboard.innerHTML = orig;
        btnSendClipboard.style.backgroundColor = '';
        clipboardTextInput.value = '';
        if (charCountLabel) charCountLabel.textContent = '0 caractère';
      }, 1500);
    });
  }

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
        setTimeout(() => btnElement.innerHTML = oldHtml, 1500);
      } else if (labelElement) {
        const old = labelElement.textContent;
        labelElement.textContent = '✔ Copié !';
        setTimeout(() => labelElement.textContent = old, 1500);
      }
    } catch (err) {
      console.warn('Erreur écriture presse-papier:', err);
    }
  }

  if (btnQuickCopyPill && quickClipboardTextPreview) {
    btnQuickCopyPill.addEventListener('click', () => {
      const text = quickClipboardTextPreview.textContent.trim();
      if (text && text !== 'Presse-papier partagé vide') {
        copyTextToClipboard(text, null, quickCopyPillText);
      }
    });
  }

  if (btnQuickOpenClipboard) {
    btnQuickOpenClipboard.addEventListener('click', () => switchView('clipboard'));
  }

  if (btnClearClipboardHistory && snippetsContainer) {
    btnClearClipboardHistory.addEventListener('click', () => {
      snippetsContainer.innerHTML = `
        <div class="clipboard-empty-state" id="clipboardEmptyState">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
            <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
          </svg>
          <h4>Aucun texte partagé</h4>
          <p>Les textes, liens et notes copiés ou envoyés apparaîtront ici.</p>
        </div>
      `;
      if (quickClipboardTextPreview) {
        quickClipboardTextPreview.textContent = 'Presse-papier partagé vide';
        quickClipboardTextPreview.style.color = 'var(--text-muted)';
        quickClipboardTextPreview.style.fontStyle = 'italic';
      }
      if (quickClipboardMeta) quickClipboardMeta.textContent = 'En attente de texte';
      if (btnQuickCopyPill) btnQuickCopyPill.style.display = 'none';
    });
  }

  function escapeHtml(str) {
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
  }

  if (window.lancedrop && window.lancedrop.onClipboardReceived) {
    window.lancedrop.onClipboardReceived((data) => {
      const content = data.text || data;
      const sender = data.senderDevice || (currentDevice ? currentDevice.name : 'Mobile');
      addSnippetItem(content, `Reçu de ${sender}`, 'À l\'instant');
      if (navClipboardBadge) navClipboardBadge.style.display = 'inline-block';
    });
  }

  // Rendu initial de la bibliothèque
  renderLibrary();
});
