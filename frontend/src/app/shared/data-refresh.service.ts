import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DataRefreshService {
  private dataRefreshSource = new Subject<void>();

  // Observable that components can subscribe to
  dataRefresh$ = this.dataRefreshSource.asObservable();

  constructor() {}

  // Method to notify subscribers that data has been refreshed
  refreshData() {
    this.dataRefreshSource.next();
  }
}
