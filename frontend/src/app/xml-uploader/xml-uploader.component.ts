import { Component, AfterViewInit, Input, OnInit, OnDestroy } from '@angular/core';
import { XmlUploaderService } from './xml-uploader.service';
import { Subscription } from 'rxjs';
import { SidebarService } from '../shared/sidebar.service';

@Component({
  selector: 'app-xml-uploader',
  templateUrl: './xml-uploader.component.html',
  styleUrls: ['./xml-uploader.component.css']
})
export class XmlUploaderComponent implements AfterViewInit, OnInit, OnDestroy {
  @Input() displayMode: 'button' | 'files' | 'both' = 'both'; // Control which part to display

  parsedFiles: { name: string; content: any }[] = [];
  uploadStatus: { [filename: string]: 'pending' | 'success' | 'error' } = {};
  uploadProgress: { [filename: string]: number } = {};
  uploadMessages: string[] = [];
  errorMessages: { [filename: string]: string } = {};
  showProgressAfterComplete: { [filename: string]: boolean } = {};

  // Overlay control
  showOverlay = false;
  isDragOver = false;


  private subscriptions: Subscription[] = [];

  constructor(
    private uploaderService: XmlUploaderService,
    private sidebarService: SidebarService
  ) {}

  ngOnInit() {
    // Subscribe to service observables
    this.subscriptions.push(
      this.uploaderService.parsedFiles$.subscribe(files => {
        this.parsedFiles = files;
        // Set up tooltip listeners after a short delay to ensure DOM is updated
        setTimeout(() => {
          this.setupTooltipListeners();
        }, 300);
      })
    );

    // Subscribe to sidebar toggle events
    this.subscriptions.push(
      this.sidebarService.toggleSidebar$.subscribe(open => {
        // If sidebar is closed, also close the overlay
        if (open === false && this.showOverlay) {
          this.showOverlay = false;
        }
      })
    );

    this.subscriptions.push(
      this.uploaderService.uploadStatus$.subscribe(status => {
        // Check for newly completed uploads
        Object.keys(status).forEach(filename => {
          if (status[filename] === 'success' && this.uploadStatus[filename] !== 'success') {
            // Set flag to show progress bar for 2 seconds after completion
            this.showProgressAfterComplete[filename] = true;
            setTimeout(() => {
              this.showProgressAfterComplete[filename] = false;
            }, 2000);
          }
        });

        this.uploadStatus = status;
      })
    );

    this.subscriptions.push(
      this.uploaderService.uploadProgress$.subscribe(progress => {
        this.uploadProgress = progress;
      })
    );

    this.subscriptions.push(
      this.uploaderService.errorMessages$.subscribe(messages => {
        this.errorMessages = messages;
      })
    );

  }


  ngAfterViewInit() {
    this.setupTooltipListeners();
  }

  ngOnDestroy() {
    // Unsubscribe from all subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  // Set up event listeners for tooltips
  setupTooltipListeners() {
    setTimeout(() => {
      const tooltips = document.querySelectorAll('.tooltip');
      tooltips.forEach(tooltip => {
        tooltip.addEventListener('mouseenter', (event) => {
          const tooltipElement = tooltip as HTMLElement;
          const tooltipText = tooltipElement.querySelector('.tooltiptext') as HTMLElement;
          if (tooltipText) {
            this.positionTooltip(event as MouseEvent, tooltipText, tooltipElement);
          }
        });
      });
    }, 500); // Small delay to ensure DOM is ready
  }

  // Position tooltip based on element position and viewport
  positionTooltip(event: MouseEvent, tooltipText: HTMLElement, tooltipElement: HTMLElement) {
    const rect = tooltipElement.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    // Calculate position based on the element's position
    const elementCenterX = rect.left + rect.width / 2;
    let left = elementCenterX - tooltipText.offsetWidth / 2;
    let top = rect.top - 10; // 10px above the element

    // Adjust if tooltip would go off screen
    const tooltipWidth = tooltipText.offsetWidth;
    const tooltipHeight = tooltipText.offsetHeight;

    // Ensure tooltip doesn't go off right edge
    if (left + tooltipWidth > viewportWidth - 20) {
      left = viewportWidth - tooltipWidth - 20;
    }

    // Ensure tooltip doesn't go off left edge
    if (left < 20) {
      left = 20;
    }

    // Ensure tooltip doesn't go off top edge
    if (top - tooltipHeight < 20) {
      top = rect.bottom + 10; // 10px below the element
    }

    // Set position
    tooltipText.style.left = `${left}px`;
    tooltipText.style.top = `${top - tooltipHeight}px`;
  }

  // Method to get error message for a specific file
  getErrorMessage(filename: string): string {
    return this.uploaderService.getErrorMessage(filename);
  }

  // Method to get upload progress for a specific file
  getUploadProgress(filename: string): number {
    return this.uploaderService.getUploadProgress(filename);
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const files = Array.from(input.files);
      this.uploaderService.processFiles(files);
      this.closeOverlayOnly(); // Close only the overlay, keep sidebar open
    }
  }

  // Method to close only the overlay without closing the sidebar
  closeOverlayOnly(): void {
    this.showOverlay = false;
    // Don't close the sidebar
  }

  // Overlay methods
  openOverlay(): void {
    // Toggle the overlay state
    this.showOverlay = !this.showOverlay;

    if (this.showOverlay) {
      // Open the sidebar
      this.sidebarService.toggleSidebar(true);

      // Position the overlay below the button after a short delay to ensure DOM is updated
      setTimeout(() => {
        this.positionOverlayBelowButton();
      }, 0);
    } else {
      // Close the sidebar
      this.sidebarService.toggleSidebar(false);
    }
  }

  // Position the overlay below the button
  private positionOverlayBelowButton(): void {
    const button = document.querySelector('.custom-file-upload') as HTMLElement;
    const overlay = document.querySelector('.overlay') as HTMLElement;
    const overlayContent = document.querySelector('.overlay-content') as HTMLElement;

    if (button && overlay && overlayContent) {
      // Since the overlay is now positioned relative to the .upload-button-container,
      // we can simply position it below the button
      overlay.style.top = `${button.offsetHeight + 10}px`; // 10px below the button

      // Calculate the right position to align with the button's right edge
      const buttonRect = button.getBoundingClientRect();
      const containerRect = document.querySelector('.upload-button-container')?.getBoundingClientRect();

      if (containerRect) {
        // Align the overlay with the button's right edge
        overlay.style.right = '0';
        overlay.style.left = 'auto';
        overlay.style.transform = 'none';
      }
    }
  }

  closeOverlay(): void {
    this.showOverlay = false;
    // Close the sidebar
    this.sidebarService.toggleSidebar(false);
  }

  closeOverlayOnOutsideClick(event: MouseEvent): void {
    // Check if the click was on the overlay background (not on the content)
    const target = event.target as HTMLElement;
    if (target.classList.contains('overlay')) {
      this.closeOverlay();
      event.stopPropagation();
    }
  }

  // Trigger file input click
  triggerFileInput(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    document.getElementById('file-upload')?.click();
  }

  // Drag and drop methods
  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
    const uploadArea = document.querySelector('.upload-area');
    uploadArea?.classList.add('drag-over');
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    const uploadArea = document.querySelector('.upload-area');
    uploadArea?.classList.remove('drag-over');
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    const uploadArea = document.querySelector('.upload-area');
    uploadArea?.classList.remove('drag-over');

    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      const files = Array.from(event.dataTransfer.files);
      this.uploaderService.processFiles(files);
      this.closeOverlayOnly(); // Close only the overlay, keep sidebar open
    }
  }
}
