export interface Customer {
  publicId: string;
  fullName: string;
  status: 'PROSPECT' | 'ACTIVE' | 'CLOSED';
  createdAt: string;
}
