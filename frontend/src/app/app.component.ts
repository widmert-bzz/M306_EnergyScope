import { Component, OnInit, OnDestroy } from '@angular/core';
import { XmlUploaderService } from './xml-uploader/xml-uploader.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'frontend';
  sidebarOpen = false;
  overallProgress = 0;
  estimatedTimeRemaining = '';
  uploadStartTime = 0;

  private subscriptions: Subscription[] = [];

  constructor(private uploaderService: XmlUploaderService) {}

  ngOnInit() {
    // Subscribe to overall progress
    this.subscriptions.push(
      this.uploaderService.overallProgress$.subscribe(progress => {
        this.overallProgress = progress;

        // Calculate estimated time remaining if upload is in progress
        if (progress > 0 && progress < 100 && this.uploadStartTime > 0) {
          this.calculateEstimatedTimeRemaining(progress);
        }
      })
    );

    // Subscribe to upload status to track when uploads start and finish
    this.subscriptions.push(
      this.uploaderService.uploadStatus$.subscribe(status => {
        // Check if any files are pending upload
        const hasPendingFiles = Object.values(status).some(s => s === 'pending');

        // If there are pending files and upload hasn't started yet, record start time
        if (hasPendingFiles && this.uploadStartTime === 0) {
          this.uploadStartTime = Date.now();
        }

        // If no files are pending, reset start time
        if (!hasPendingFiles) {
          this.uploadStartTime = 0;
          this.estimatedTimeRemaining = '';
        }
      })
    );
  }

  // Calculate estimated time remaining based on progress and elapsed time
  private calculateEstimatedTimeRemaining(progress: number): void {
    if (progress <= 0) {
      this.estimatedTimeRemaining = '';
      return;
    }

    const elapsedMs = Date.now() - this.uploadStartTime;
    const estimatedTotalMs = (elapsedMs / progress) * 100;
    const remainingMs = estimatedTotalMs - elapsedMs;

    if (remainingMs <= 0) {
      this.estimatedTimeRemaining = '';
      return;
    }

    // Format time remaining
    const seconds = Math.floor(remainingMs / 1000);
    if (seconds < 60) {
      this.estimatedTimeRemaining = `${seconds} Sekunden`;
    } else {
      const minutes = Math.floor(seconds / 60);
      const remainingSeconds = seconds % 60;
      this.estimatedTimeRemaining = `${minutes} Minuten ${remainingSeconds} Sekunden`;
    }
  }

  ngOnDestroy() {
    // Unsubscribe from all subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  toggleSidebar() {
    this.sidebarOpen = !this.sidebarOpen;
  }
}
